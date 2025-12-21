package com.szu.afternoon3.platform.component;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.FoundWord;
import cn.hutool.dfa.WordTree;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 敏感词过滤器 (纯内存版)
 * 启动时自动扫描 resources/sensitive 目录下的所有 txt 文件加载
 */
@Slf4j
@Component
public class SensitiveWordFilter {

    private final WordTree wordTree = new WordTree();

    // 修改 1: 扫描 .dat 文件
    private static final String DICT_PATH = "classpath*:sensitive/*.dat";

    // 修改 2: 必须与加密时的密钥完全一致
    private static final byte[] KEY = "REDNOTE_SECURE_2025".getBytes(StandardCharsets.UTF_8);

    @PostConstruct
    public void init() {
        log.info("开始加载加密敏感词库...");
        long start = System.currentTimeMillis();
        Set<String> allWords = new HashSet<>();

        try {
            ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(DICT_PATH);

            if (resources.length == 0) {
                log.warn("未发现敏感词文件");
                return;
            }

            for (Resource resource : resources) {
                try (InputStream inputStream = resource.getInputStream()) {
                    // 1. 读取所有字节
                    byte[] data = IoUtil.readBytes(inputStream);

                    // 2. 解密 (XOR)
                    for (int i = 0; i < data.length; i++) {
                        data[i] ^= KEY[i % KEY.length];
                    }

                    // 3. 还原为字符串并按行分割
                    String content = new String(data, StandardCharsets.UTF_8);
                    // 兼容 Windows(\r\n) 和 Linux(\n) 换行符
                    String[] lines = content.split("\\r?\\n");

                    int fileCount = 0;
                    for (String line : lines) {
                        if (StrUtil.isNotBlank(line)) {
                            allWords.add(line.trim());
                            fileCount++;
                        }
                    }
                    log.info("加载并解密词库: {}, 词条数: {}", resource.getFilename(), fileCount);
                } catch (Exception e) {
                    log.error("解密敏感词文件失败: {}", resource.getFilename(), e);
                }
            }

            if (CollUtil.isNotEmpty(allWords)) {
                wordTree.addWords(allWords);
            }

        } catch (IOException e) {
            log.error("扫描敏感词文件异常", e);
        }

        log.info("敏感词库加载完毕，共 {} 条，耗时 {} ms", allWords.size(), System.currentTimeMillis() - start);
    }

    /**
     * 判断是否包含敏感词
     */
    public boolean hasSensitiveWord(String text) {
        if (StrUtil.isBlank(text)) return false;
        return wordTree.isMatch(text);
    }

    /**
     * 提取文本中的所有敏感词
     */
    public List<String> findAll(String text) {
        if (StrUtil.isBlank(text)) return CollUtil.newArrayList();
        return wordTree.matchAll(text, -1, false, false);
    }

    /**
     * 敏感词脱敏 (将敏感词替换为 *)
     */
    public String filter(String text) {
        if (StrUtil.isBlank(text)) return text;
        
        // 查找所有匹配的词
        List<FoundWord> foundWords = wordTree.matchAllWords(text, -1, false, false);
        if (CollUtil.isEmpty(foundWords)) {
            return text;
        }

        StringBuilder result = new StringBuilder(text);
        // 倒序替换，防止索引错位
        for (int i = foundWords.size() - 1; i >= 0; i--) {
            FoundWord fw = foundWords.get(i);
            String stars = StrUtil.repeat('*', fw.getWord().length());
            result.replace(fw.getStartIndex(), fw.getEndIndex() + 1, stars);
        }
        return result.toString();
    }
}