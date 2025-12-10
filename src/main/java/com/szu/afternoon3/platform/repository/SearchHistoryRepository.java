package com.szu.afternoon3.platform.repository;

import com.szu.afternoon3.platform.entity.mongo.SearchHistoryDoc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SearchHistoryRepository extends MongoRepository<SearchHistoryDoc, String> {

    /**
     * 分页查询某用户的搜索历史
     * 通常按 updatedAt 倒序
     */
    Page<SearchHistoryDoc> findByUserId(Long userId, Pageable pageable);

    /**
     * 清空某用户的搜索历史
     */
    void deleteByUserId(Long userId);
}