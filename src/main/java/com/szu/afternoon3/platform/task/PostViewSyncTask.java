package com.szu.afternoon3.platform.task;

import cn.hutool.core.collection.CollUtil;
import com.szu.afternoon3.platform.entity.mongo.PostDoc;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class PostViewSyncTask {

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private MongoTemplate mongoTemplate;

    private static final String CACHE_POST_VIEWS = "rednote:post:views:buffer";

    /**
     * 每 1 分钟执行一次同步
     * 策略：读取 Redis -> 批量更新 Mongo -> 删除 Redis 已处理的 Key
     */
    @Scheduled(fixedRate = 60000) // 60秒
    public void syncPostViewsToMongo() {
        // 1. 获取 Redis 中所有的浏览计数
        Map<Object, Object> viewMap = redisTemplate.opsForHash().entries(CACHE_POST_VIEWS);
        if (CollUtil.isEmpty(viewMap)) {
            return;
        }

        log.info("开始同步浏览量，共 {} 条数据", viewMap.size());

        // 2. 准备批量操作 (BulkOperations 性能远高于循环 save)
        BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, PostDoc.class);

        for (Map.Entry<Object, Object> entry : viewMap.entrySet()) {
            String postId = (String) entry.getKey();
            // Redis 里存的是字符串，转成 Integer
            Integer increment = Integer.parseInt((String) entry.getValue());

            Query query = Query.query(Criteria.where("id").is(postId));
            Update update = new Update().inc("viewCount", increment); // 累加
            
            bulkOps.updateOne(query, update);
        }

        // 3. 执行批量更新
        bulkOps.execute();

        // 4. 清理 Redis (这里有个并发小坑，生产环境通常用 Lua 脚本原子删除，
        // 但为了简化，我们直接删除 Key。在这 1 秒间产生的新增量可能会丢失，这对浏览量来说可接受)
        // 更好的做法是：刚才读了哪些 Key，就只从 Hash 里删掉哪些 Key
        redisTemplate.opsForHash().delete(CACHE_POST_VIEWS, viewMap.keySet().toArray());

        log.info("浏览量同步完成");
    }
}