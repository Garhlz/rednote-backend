package com.szu.afternoon3.platform.controller;

import cn.hutool.core.util.StrUtil;
import com.szu.afternoon3.platform.annotation.OperationLog;
import com.szu.afternoon3.platform.common.Result;
import com.szu.afternoon3.platform.dto.PostTagSuggestDTO;
import com.szu.afternoon3.platform.enums.ResultCode;
import com.szu.afternoon3.platform.service.PostService;
import com.szu.afternoon3.platform.service.impl.AiServiceImpl;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 标签控制器
 * 提供热门标签等元数据查询
 */
@RestController
@RequestMapping("/api/tag")
public class TagController {

    @Autowired
    private PostService postService;
    @Autowired
    private AiServiceImpl aiService; // 注入 AI 服务
    /**
     * 获取热门标签
     */
    @GetMapping("/hot")
    @OperationLog(module = "标签模块", description = "获取热门标签")
    public Result<List<String>> getHotTags() {
        List<String> tags = postService.getHotTags(10);
        return Result.success(tags);
    }

    /**
     * AI 智能生成标签 (编辑时调用)
     */
    @PostMapping("/ai-generate")
    @OperationLog(module = "帖子模块", description = "AI生成标签")
    public Result<List<String>> aiGenerateTags(@RequestBody @Valid PostTagSuggestDTO dto) {
        // 1. 简单校验
        if (StrUtil.length(dto.getContent()) > 2000) {
            return Result.error(ResultCode.PARAM_ERROR.getCode(), "内容过长，请精简后重试");
        }

        // 2. 同步调用 AI
        List<String> tags = aiService.generateTags(dto.getTitle(), dto.getContent());

        // 3. 返回结果
        return Result.success(tags);
    }
}