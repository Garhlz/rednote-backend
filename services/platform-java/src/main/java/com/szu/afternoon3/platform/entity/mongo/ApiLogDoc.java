package com.szu.afternoon3.platform.entity.mongo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Data
@Document(collection = "api_logs")
public class ApiLogDoc {
    @Id
    private String id;

    // --- 核心分类 ---
    @Indexed
    private String logType;   // "USER_OPER" / "ADMIN_OPER"
    @Indexed
    private String module;    // "用户", "帖子", "互动"
    private String description;

    // --- 【新增】核心业务对象ID (SpEL提取) ---
    // 存储 postId, targetUserId 等，用于管理员点击跳转
    @Indexed
    private String bizId;

    // --- 基础信息 ---
    @Indexed
    private String traceId;
    @Indexed
    private Long userId;      // 操作人 ID
    private String username;
    private String role;

    @Indexed
    private String method;
    @Indexed
    private String uri;

    private String ip;
    private String params;    // 完整参数 (包含所有辅助ID)
    private Integer status;
    private String errorMsg;
    private Long timeCost;

    // 90天 * 24小时 * 60分钟 * 60秒 = 7776000
    @Indexed(expireAfterSeconds = 2592000)
    private LocalDateTime createdAt;
}