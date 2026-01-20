package com.szu.afternoon3.platform.repository;

import java.util.List;

public interface SearchHistoryRepositoryCustom {
    /**
     * 聚合查询：查找以 keyword 开头的热门搜索词
     * @param keyword 前缀关键词
     * @param limit 限制条数
     * @return 关键词列表
     */
    List<String> findHotKeywordsStartingWith(String keyword, int limit);
}