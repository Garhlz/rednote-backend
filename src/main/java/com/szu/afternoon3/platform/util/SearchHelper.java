package com.szu.afternoon3.platform.util;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.huaban.analysis.jieba.JiebaSegmenter;
import com.huaban.analysis.jieba.SegToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SearchHelper {
    private final JiebaSegmenter segmenter = new JiebaSegmenter();

    public List<String> generateSearchTerms(String title, String content, List<String> tags) {
        Set<String> terms = new HashSet<>();

        if (StrUtil.isNotBlank(title)) {
            // 【关键修改】改用 INDEX 模式，切得更碎
            // 例如 "深圳大学" -> "深圳", "大学", "深圳大学"
            processText(title, terms, JiebaSegmenter.SegMode.INDEX);
        }

        if (StrUtil.isNotBlank(content)) {
            // 内容依然用 SEARCH 模式，避免索引爆炸
            processText(content, terms, JiebaSegmenter.SegMode.SEARCH);
        }

        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                terms.add(tag); // 保留原标签
                // 标签通常较短，用 INDEX 模式切分更保险
                processText(tag, terms, JiebaSegmenter.SegMode.INDEX);
            }
        }

        return new ArrayList<>(terms);
    }

    public String analyzeKeyword(String keyword) {
        if (StrUtil.isBlank(keyword)) return "";
        List<SegToken> tokens = segmenter.process(keyword, JiebaSegmenter.SegMode.SEARCH);
        return tokens.stream()
                .map(t -> t.word.toLowerCase().trim()) // 转小写
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.joining(" "));
    }

    private void processText(String text, Set<String> result, JiebaSegmenter.SegMode mode) {
        if (StrUtil.isBlank(text)) return;

        List<SegToken> tokens = segmenter.process(text, mode);
        for (SegToken token : tokens) {
            String word = token.word;
            // 过滤逻辑保持不变：长度>1 或 纯字母/数字
            if (word.length() > 1 || word.matches("[a-zA-Z]+") || StrUtil.isNumeric(word)) {
                result.add(word.toLowerCase());
            }
        }
    }
}