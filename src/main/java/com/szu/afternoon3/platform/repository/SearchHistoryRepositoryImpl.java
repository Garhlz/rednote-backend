package com.szu.afternoon3.platform.repository;

import com.szu.afternoon3.platform.entity.mongo.SearchHistoryDoc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class SearchHistoryRepositoryImpl implements SearchHistoryRepositoryCustom {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public List<String> findHotKeywordsStartingWith(String keyword, int limit) {
        // 1. 构建正则：以 keyword 开头，不区分大小写
        // Pattern.quote 避免输入包含 *?() 等特殊字符导致报错
        String regex = "^" + Pattern.quote(keyword);

        // 2. 构建聚合管道
        Aggregation aggregation = Aggregation.newAggregation(
                // A. Match: 筛选出匹配前缀的记录
                Aggregation.match(Criteria.where("keyword").regex(regex, "i")),
                
                // B. Group: 按关键词分组并计数 (统计全局热度)
                Aggregation.group("keyword").count().as("count"),
                
                // C. Sort: 按热度倒序
                Aggregation.sort(Sort.Direction.DESC, "count"),
                
                // D. Limit: 取前 N 个
                Aggregation.limit(limit),
                
                // E. Project: 只保留 _id (即关键词本身)
                Aggregation.project("_id")
        );

        // 3. 执行查询
        // Group 之后，"_id" 存放的就是关键词
        AggregationResults<Map> results = mongoTemplate.aggregate(
            aggregation, SearchHistoryDoc.class, Map.class
        );

        // 4. 提取结果
        List<String> hotWords = new ArrayList<>();
        for (Map row : results.getMappedResults()) {
            String word = (String) row.get("_id");
            if (word != null) {
                hotWords.add(word);
            }
        }
        return hotWords;
    }
}