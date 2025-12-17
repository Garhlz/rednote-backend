package com.szu.afternoon3.platform.listener;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.rabbitmq.client.Channel;
import com.szu.afternoon3.platform.config.RabbitConfig;
import com.szu.afternoon3.platform.entity.es.PostEsDoc;
import com.szu.afternoon3.platform.entity.mongo.PostDoc;
import com.szu.afternoon3.platform.event.*;
import com.szu.afternoon3.platform.repository.PostRepository;
import com.szu.afternoon3.platform.repository.es.PostEsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.*;
import org.springframework.data.elasticsearch.core.suggest.Completion;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
@RabbitListener(queues = RabbitConfig.QUEUE_ES_SYNC)
public class PostEsSyncListener {

    @Autowired
    private PostEsRepository postEsRepository;

    @Autowired
    private PostRepository postRepository; // 需要注入 Mongo Repository 用于回查

    @Autowired
    private ElasticsearchOperations esOperations;

    @Value("${app.post.audit-enable:false}")
    private boolean auditEnable;


    public void onSyncEvent(@Payload Object event, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) {
        // 【添加这行调试日志】看看收到的到底是什么鬼
        log.info("收到 ES 同步消息，类型: {}", event.getClass().getName());
        try {
            if (event instanceof PostCreateEvent) {
                // 如果没有开启审核，Create 事件直接入库 ES
                if (!auditEnable) {
                    handleCreate((PostCreateEvent) event);
                }
            }
            else if (event instanceof PostAuditPassEvent) {
                // 审核通过事件，无条件入库 ES
                handleAuditPass((PostAuditPassEvent) event);
            }
            else if (event instanceof UserUpdateEvent) {
                // 用户修改资料，批量更新 ES 中的 redundancy 字段
                handleUserUpdate((UserUpdateEvent) event);
            }
            else if (event instanceof PostDeleteEvent) {
                // 删除帖子
                postEsRepository.deleteById(((PostDeleteEvent) event).getPostId());
                log.info("ES Delete: {}", ((PostDeleteEvent) event).getPostId());
            }
            else if (event instanceof PostUpdateEvent) {
                // 修改帖子，逻辑最复杂，需要回查 Mongo
                handleUpdate((PostUpdateEvent) event);
            }

            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("ES Sync Failed: {}", e.getMessage(), e);
            try {
                // 失败不重试，防止死循环 (或者你可以放入死信队列)
                channel.basicNack(tag, false, false);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    // 1. 处理直接发布
    @RabbitHandler
    private void handleCreate(PostCreateEvent e) {
        PostEsDoc doc = new PostEsDoc();
        // 填充基础数据
        BeanUtils.copyProperties(e,doc);

        doc.setLikeCount(0);
        doc.setCreatedAt(LocalDateTime.now()); // Event 里可能没时间
        postEsRepository.save(doc);
        log.info("ES Add (Direct): {}", e.getId());
    }

    // 2. 处理审核通过
    @RabbitHandler
    private void handleAuditPass(PostAuditPassEvent e) {

        PostEsDoc doc = new PostEsDoc();
        BeanUtils.copyProperties(e,doc);

        if (doc.getLikeCount() == null) doc.setLikeCount(0);
        if (doc.getCreatedAt() == null) doc.setCreatedAt(LocalDateTime.now());
        postEsRepository.save(doc);
        log.info("ES Add (Audit Pass): {}", e.getId());
    }

    // 3. 处理帖子更新 (核心逻辑)
    @RabbitHandler
    private void handleUpdate(PostUpdateEvent event) {
        // 必须回查 MongoDB, 因为涉及最终一致性
        // 需要检查 status 和 isDeleted 状态
        PostDoc mongoDoc = postRepository.findById(event.getPostId()).orElse(null);

        // 如果帖子不存在，或者被逻辑删除，或者状态不是发布(1)，则从 ES 中移除
        if (mongoDoc == null || (mongoDoc.getIsDeleted() != null && mongoDoc.getIsDeleted() == 1)
                || (mongoDoc.getStatus() != null && mongoDoc.getStatus() != 1)) {
            postEsRepository.deleteById(event.getPostId());
            log.info("ES Remove (Update Invalid): {}", event.getPostId());
            return;
        }

        // 数据有效，覆盖更新 ES
        PostEsDoc doc = new PostEsDoc();
        BeanUtils.copyProperties(mongoDoc, doc);
        postEsRepository.save(doc);
        log.info("ES Update: {}", mongoDoc.getId());
    }

    // 4. 处理用户资料变更 (Update By Query)
    @RabbitHandler
    private void handleUserUpdate(UserUpdateEvent event) {
        Criteria criteria = new Criteria("userId").is(event.getUserId());
        Query query = new CriteriaQuery(criteria);

        String scriptCode = "ctx._source.userNickname = params.nickname; ctx._source.userAvatar = params.avatar;";

        Map<String, Object> params = new HashMap<>();
        params.put("nickname", event.getNewNickname());
        params.put("avatar", event.getNewAvatar());

        UpdateQuery updateQuery = UpdateQuery.builder(query)
                .withScriptType(ScriptType.INLINE)
                .withScript(scriptCode)
                .withParams(params)
                .build();

        esOperations.updateByQuery(updateQuery, IndexCoordinates.of("post_index"));
        log.info("ES User Info Updated: userId={}", event.getUserId());
    }

}