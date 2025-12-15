package com.szu.afternoon3.platform.repository;

import com.szu.afternoon3.platform.entity.mongo.SearchHistoryDoc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

// 【修改点】同时继承 MongoRepository 和 SearchHistoryRepositoryCustom
@Repository
public interface SearchHistoryRepository extends MongoRepository<SearchHistoryDoc, String>, SearchHistoryRepositoryCustom {

    Page<SearchHistoryDoc> findByUserId(Long userId, Pageable pageable);

    void deleteByUserId(Long userId);

    void deleteByUserIdAndKeyword(Long userId, String keyword);
}