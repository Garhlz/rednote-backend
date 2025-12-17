package com.szu.afternoon3.platform.controller; // 建议放在 controller 包下

import cn.hutool.core.collection.CollUtil;
import com.szu.afternoon3.platform.entity.es.PostEsDoc;
import com.szu.afternoon3.platform.entity.mongo.PostDoc;
import com.szu.afternoon3.platform.repository.PostRepository;
import com.szu.afternoon3.platform.repository.es.PostEsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/admin/sync") // 建议使用 admin 路径隔离
public class DataSyncController {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostEsRepository postEsRepository;

    // 从配置文件读取一个简单的密钥，防止外人误触发 (可在 application.yml 配置 admin-secret)
    @Value("${app.admin.secret:szu123}") 
    private String adminSecret;

    /**
     * 全量同步 Mongo -> ES
     * 访问方式: POST http://localhost:8080/admin/sync/mongo-to-es
     * Header: X-Admin-Token: szu123
     */
    @PostMapping("/mongo-to-es")
    public Map<String, Object> syncAllMongoToEs(@RequestHeader(value = "X-Admin-Token", required = false) String token) {
        Map<String, Object> result = new HashMap<>();

        // 1. 简单的安全检查
        if (token == null || !token.equals(adminSecret)) {
            result.put("code", 403);
            result.put("message", "无权操作，Token 错误");
            return result;
        }

        log.info("========== 开始全量同步 Mongo -> ES (触发者: Controller) ==========");
        long startTime = System.currentTimeMillis();

        // 2. 清空旧数据 (注意：这会导致短时间内搜索为空)
        postEsRepository.deleteAll();
        log.info("旧索引数据已清空");

        int page = 0;
        int size = 100; 
        int totalSynced = 0;

        while (true) {
            // 分页查询 Mongo
            Page<PostDoc> mongoPage = postRepository.findByStatusAndIsDeleted(1, 0, PageRequest.of(page, size));
            List<PostDoc> mongoDocs = mongoPage.getContent();

            if (CollUtil.isEmpty(mongoDocs)) {
                break;
            }

            List<PostEsDoc> esDocs = new ArrayList<>();
            for (PostDoc mongoDoc : mongoDocs) {
                PostEsDoc esDoc = new PostEsDoc();
                
                BeanUtils.copyProperties(mongoDoc, esDoc);

                // 确保非空和时间处理
                if (esDoc.getLikeCount() == null) esDoc.setLikeCount(0);
                
                // 时间处理逻辑保持原样
                if (mongoDoc.getCreatedAt() != null) {
                    esDoc.setCreatedAt(mongoDoc.getCreatedAt());
                } else {
                    esDoc.setCreatedAt(LocalDateTime.now());
                }
                
                esDocs.add(esDoc);
            }

            postEsRepository.saveAll(esDocs);
            
            totalSynced += esDocs.size();
            log.info("已同步第 {} 页，本页 {} 条", page + 1, esDocs.size());

            if (!mongoPage.hasNext()) {
                break;
            }
            page++;
        }

        long endTime = System.currentTimeMillis();
        String msg = String.format("同步完成，共 %d 条，耗时 %d ms", totalSynced, (endTime - startTime));
        log.info(msg);

        result.put("code", 200);
        result.put("message", msg);
        result.put("total", totalSynced);
        
        return result;
    }
}