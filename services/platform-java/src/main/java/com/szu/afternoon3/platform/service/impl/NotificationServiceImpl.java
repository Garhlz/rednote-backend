package com.szu.afternoon3.platform.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.szu.afternoon3.platform.entity.mongo.NotificationDoc;
import com.szu.afternoon3.platform.enums.NotificationType;
import com.szu.afternoon3.platform.repository.NotificationRepository;
import com.szu.afternoon3.platform.service.NotificationService;
import com.szu.afternoon3.platform.vo.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private MongoTemplate mongoTemplate; // 用于高效的批量更新

    @Override
    public long countUnread(Long userId) {
        return notificationRepository.countByReceiverIdAndIsReadFalse(userId);
    }

    @Override
    public PageResult<NotificationDoc> getMyNotifications(Long userId, Integer page, Integer size) {
        // 1. 处理分页参数 (默认值)
        int currentPage = (page == null || page < 1) ? 1 : page;
        int pageSize = (size == null || size < 1) ? 20 : size;

        // 2. 构建分页请求 (注意：Spring Data JPA/Mongo 的页码是从 0 开始的)
        Pageable pageable = PageRequest.of(currentPage - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        // 3. 查询数据
        Page<NotificationDoc> pageData = notificationRepository.findByReceiverId(userId, pageable);

        // 4. 使用通用 VO 包装返回
        // PageResult.of(数据列表, 总数, 当前页码, 每页大小)
        return PageResult.of(
                pageData.getContent(),
                pageData.getTotalElements(),
                currentPage,
                pageSize
        );
    }

    @Override
    public void markAllAsRead(Long userId) {
        // 使用 MongoTemplate 进行批量更新，性能优于先查后改
        // update notifications set isRead = true where receiverId = userId and isRead = false
        Query query = Query.query(Criteria.where("receiverId").is(userId).and("isRead").is(false));
        Update update = new Update().set("isRead", true);
        
        mongoTemplate.updateMulti(query, update, NotificationDoc.class);
    }

    @Override
    public void markBatchAsRead(Long userId, List<String> ids) {
        // 1. 判空，避免无效查库
        if (ids == null || ids.isEmpty()) {
            return;
        }

        // 2. 构建查询条件
        // 核心逻辑：ID 在列表里 AND 接收者是当前用户 AND 状态是未读
        // 加上 receiverId 是为了权限控制，防止有人恶意传别人的消息ID
        Query query = Query.query(Criteria.where("_id").in(ids)
                .and("receiverId").is(userId)
                .and("isRead").is(false));

        // 3. 构建更新内容
        Update update = new Update().set("isRead", true);

        // 4. 执行批量更新
        mongoTemplate.updateMulti(query, update, NotificationDoc.class);
    }

    @Override
    public void save(NotificationDoc doc) {
        // 【核心修改】区分消息类型进行处理
        if (isStatusNotification(doc.getType())) {
            // 1. 对于 点赞/收藏/关注，执行 "Upsert" (存在即更新，不存在即插入)
            handleUpsert(doc);
        } else {
            // 2. 对于 评论/回复/系统通知，保留每一条记录 (不去重)
            notificationRepository.save(doc);
        }
    }

    /**
     * 判断是否为状态类通知 (需要去重)
     */
    private boolean isStatusNotification(NotificationType type) {
        return type == NotificationType.LIKE_POST ||
                type == NotificationType.COLLECT_POST ||
                type == NotificationType.RATE_POST ||
                type == NotificationType.LIKE_COMMENT ||
                type == NotificationType.FOLLOW;
    }

    /**
     * 执行去重更新逻辑
     */
    private void handleUpsert(NotificationDoc doc) {
        // 定义唯一键：接收者 + 发送者 + 类型 + 目标ID
        // 例如：A 对 帖子B 点赞，数据库里只能有一条这样的记录
        Query query = Query.query(Criteria.where("receiverId").is(doc.getReceiverId())
                .and("senderId").is(doc.getSenderId())
                .and("type").is(doc.getType())
                .and("targetId").is(doc.getTargetId()));

        Update update = new Update()
                // 更新可能会变的信息 (比如用户改了头像昵称)
                .set("senderNickname", doc.getSenderNickname())
                .set("senderAvatar", doc.getSenderAvatar())
                .set("targetPreview", doc.getTargetPreview())
                // 【关键】重置为未读，并更新时间到当前
                // 这样这条旧消息会重新“浮”到列表最上面，并变回未读状态
                .set("isRead", false)
                .set("createdAt", LocalDateTime.now());
                // 如果是新插入，自动设置 created (虽然上面set覆盖了，但保留这行语义更清晰)
//                .setOnInsert("createdAt", LocalDateTime.now());

        // 执行 Upsert
        mongoTemplate.upsert(query, update, NotificationDoc.class);
    }

    /**
     * 清洗历史重复通知数据
     * 只保留最新的一条，删除其余重复的
     * @return 删除的条数
     */
    /**
     * 清洗历史重复通知数据 (修复版)
     */
    public long cleanDuplicateNotifications() {
        // 1. 定义哪些类型的通知需要去重
        List<NotificationType> typesToCheck = Arrays.asList(
                NotificationType.LIKE_POST,
                NotificationType.COLLECT_POST,
                NotificationType.RATE_POST,
                NotificationType.FOLLOW,
                NotificationType.LIKE_COMMENT
        );

        // 2. 构建聚合查询
        Aggregation agg = Aggregation.newAggregation(
                // A. 筛选
                Aggregation.match(Criteria.where("type").in(typesToCheck)),
                // B. 排序：按时间倒序
                Aggregation.sort(Sort.Direction.DESC, "createdAt"),
                // C. 分组
                Aggregation.group("receiverId", "senderId", "type", "targetId")
                        .first("_id").as("latestId")
                        .push("_id").as("allIds")
        );

        // 3. 执行聚合
        AggregationResults<Map> results = mongoTemplate.aggregate(agg, "notifications", Map.class);
        List<Map> groupList = results.getMappedResults();

        List<String> idsToDelete = new ArrayList<>();

        // 4. 分析结果
        for (Map group : groupList) {
            // 【核心修复点】: 不要强转 (String)，而是处理 Object
            // allIds 也是一个包含 ObjectId 的列表
            List<?> rawIds = (List<?>) group.get("allIds");

            // latestId 是一个 ObjectId 对象
            Object latestIdObj = group.get("latestId");
            String latestIdStr = latestIdObj.toString();

            if (rawIds != null && rawIds.size() > 1) {
                for (Object idObj : rawIds) {
                    // 转成 String 进行比对
                    String idStr = idObj.toString();

                    // 如果不是最新的那个 ID，就加入删除列表
                    if (!idStr.equals(latestIdStr)) {
                        idsToDelete.add(idStr);
                    }
                }
            }
        }

        // 5. 批量删除
        if (!idsToDelete.isEmpty()) {
            // 分批删除防止内存溢出
            List<List<String>> partitions = CollUtil.split(idsToDelete, 1000);
            for (List<String> batch : partitions) {
                // Spring Data 会自动处理这里的 String -> ObjectId 转换
                Query deleteQuery = Query.query(Criteria.where("_id").in(batch));
                mongoTemplate.remove(deleteQuery, NotificationDoc.class);
            }
        }

        log.info("数据清洗完成，共删除了 {} 条重复通知", idsToDelete.size());
        return idsToDelete.size();
    }
}