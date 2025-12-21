package com.szu.afternoon3.platform.entity.mongo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 帖子审核记录流水表
 */
@Data
@Document(collection = "post_audit_logs")
public class PostAuditLogDoc {
    @Id
    private String id;

    private String postId;      // 关联的帖子ID
    private String postTitle;   // 帖子标题快照

    private Long operatorId;    // 操作人ID (管理员ID)
    private String operatorName;// 操作人昵称 (管理员昵称)

    private Integer auditStatus;// 审核结果: 1-通过, 2-拒绝, 3-强制删除
    private String rejectReason;// 拒绝理由 (仅拒绝时有值)

    private LocalDateTime createdAt; // 审核时间
}