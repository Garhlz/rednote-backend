package com.szu.afternoon3.platform.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.szu.afternoon3.platform.service.AiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class AiServiceImpl implements AiService {

    @Value("${ai.deepseek.url}") private String apiUrl;
    @Value("${ai.deepseek.key}") private String apiKey;
    @Value("${ai.deepseek.model}") private String model;

    /**
     * 通用 DeepSeek 调用方法
     * @param systemPrompt 系统提示词 (设定角色)
     * @param userContent 用户输入
     * @param temperature 温度 (根据文档：标签生成1.0，对话1.3)
     */
    private String callDeepSeek(String systemPrompt, String userContent, double temperature) {
        // 1. 记录调用开始时间
        long startTime = System.currentTimeMillis();
        String apiCallName = "DeepSeek_LLM_Call"; // 用于日志标识

        try {
            // 1. 构建请求体 (参考 OpenAI/DeepSeek 格式)
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("temperature", temperature);
            body.put("stream", false);

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));
            messages.add(Map.of("role", "user", "content", userContent));
            body.put("messages", messages);

            // 2. 发起 HTTP POST 请求
            String result = HttpRequest.post(apiUrl)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .body(JSONUtil.toJsonStr(body))
                    .timeout(30000)
                    .execute()
                    .body();

            // 3. 解析响应
            JSONObject json = JSONUtil.parseObj(result);
            String content = null;

            // 检查是否有错误
            if (json.containsKey("error")) {
                // 记录失败日志，包含耗时
                long endTime = System.currentTimeMillis();
                long duration = endTime - startTime;
                log.error("[{}] 调用失败，耗时: {} ms，错误详情: {}",
                        apiCallName, duration, json.get("error"));
                return null;
            }

            // 提取 content
            content = json.getByPath("choices[0].message.content", String.class);

            // 4. 记录成功日志，包含耗时
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            log.info("[{}] 调用成功，耗时: {} ms，响应内容长度: {}",
                    apiCallName, duration, content != null ? content.length() : 0);

            return content;

        } catch (Exception e) {
            // 记录异常日志，包含耗时
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            log.error("[{}] 调用异常，耗时: {} ms", apiCallName, duration, e);
            return null;
        }
    }

    /**
     * 场景 1: 根据内容生成标签
     * 文档建议 Temperature: 1.0 (数据分析)
     */
    /**
     * 生成标签 (同步方法)
     */
    public List<String> generateTags(String title, String content) {
        // 如果内容太少，直接不调 AI，省钱
        if (StrUtil.length(content) < 5) {
            return Collections.emptyList();
        }

        String systemPrompt = """
            你是一个社交平台助手。根据用户输入生成 3-5 个热门标签。
            要求：
            1. 直接返回 JSON 字符串数组，例如：["美食", "探店"]
            2. 不要包含 Markdown 格式（不要 ```json ），不要任何解释语。
            3. 标签简短有力，不带 # 号。
            """;

        String input = String.format("标题：%s\n内容：%s", StrUtil.nullToEmpty(title), content);

        String response = callDeepSeek(systemPrompt, input, 1.0);

        if (StrUtil.isBlank(response)) return new ArrayList<>();

        try {
            // 清洗数据
            String cleanJson = response.replace("```json", "").replace("```", "").trim();
            // 兼容性处理：有时候 AI 会返回 [标签1, 标签2] 而不是标准的 JSON 双引号
            // Hutool 的 parseArray 比较智能，通常能处理
            JSONArray array = JSONUtil.parseArray(cleanJson);
            return array.toList(String.class);
        } catch (Exception e) {
            log.warn("AI 标签解析失败: {}", response);
            return new ArrayList<>();
        }
    }

    public String generatePostSummary(String title, String content) {
        // 如果内容太短，没必要总结，省钱
        if (StrUtil.length(content) < 10) {
            return null;
        }

        String input = String.format("标题：%s\n内容：%s", StrUtil.nullToEmpty(title), content);

        String systemPrompt = """
            你是一个社交平台的“课代表”机器人。
            任务：阅读用户的帖子标题和内容，用幽默、简短的语言进行总结（神总结/TL;DR）。
            要求：
            1. 必须以第一人称评论的口吻，不要像机器摘要。
            2. 字数控制在 60 字以内。
            3. 开头可以是“课代表总结：”或者“省流：”。
            4. 如果内容是在发泄情绪，给予暖心安慰。
            """;

        return callDeepSeek(systemPrompt, input, 1.3); // 1.3 温度让总结更有趣
    }

    /**
     * 场景 4: 评论区交互式回复 (被 @ 时触发)
     * @param postTitle 帖子标题
     * @param postContent 帖子内容
     * @param parentContent 父评论内容 (如果是二级回复则有值，否则为 null)
     * @param userPrompt 用户发送的评论内容
     */
    public String generateInteractiveReply(String postTitle, String postContent, String parentContent, String userPrompt) {

        // 1. 构建系统提示词
        String systemPrompt = """
            你是一个社交平台的高情商AI助手，名字叫"小映"。
            用户在评论区 @了你，你需要根据帖子内容回答用户的问题。
            要求：
            1. 语气亲切、活泼，像个真实的朋友。
            2. 结合帖子上下文回答。
            3. 回复尽量简短（80字以内），不要长篇大论。
            4. 如果用户是在闲聊，就幽默回应。
            """;

        // 2. 构建用户输入上下文
        StringBuilder inputBuilder = new StringBuilder();
        inputBuilder.append("【当前帖子上下文】\n")
                .append("标题：").append(StrUtil.nullToEmpty(postTitle)).append("\n")
                .append("内容：").append(StrUtil.subPre(postContent, 500)).append("\n\n"); // 截取500字防止超长

        if (StrUtil.isNotBlank(parentContent)) {
            inputBuilder.append("【用户回复的目标评论】\n")
                    .append(parentContent).append("\n\n");
        }

        inputBuilder.append("【用户对你说的话】\n")
                .append(userPrompt);

        // 3. 调用 AI (温度 1.3 比较活泼)
        return callDeepSeek(systemPrompt, inputBuilder.toString(), 1.3);
    }
}