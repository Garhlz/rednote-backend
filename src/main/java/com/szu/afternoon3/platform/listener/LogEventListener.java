package com.szu.afternoon3.platform.listener;

import cn.hutool.json.JSONUtil; // 引入 Hutool
import com.szu.afternoon3.platform.config.RabbitConfig;
import com.szu.afternoon3.platform.entity.mongo.ApiLogDoc;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RabbitListener(queues = RabbitConfig.QUEUE_LOG)
public class LogEventListener {

    @Autowired
    private MongoTemplate mongoTemplate;

    @RabbitHandler
    public void handleLog(ApiLogDoc logDoc) {
        try {
            // 1. 保存到数据库
            mongoTemplate.save(logDoc);

            // 2. 【核心】在这里加上打印语句，使用 INFO 级别
            log.info("[日志落库] {} - {} | 耗时: {}ms | 用户: {}",
                    logDoc.getModule(),
                    logDoc.getDescription(),
                    logDoc.getTimeCost(),
                    logDoc.getUserId());

        } catch (Exception e) {
            log.error("日志落库失败", e);
        }
    }
}