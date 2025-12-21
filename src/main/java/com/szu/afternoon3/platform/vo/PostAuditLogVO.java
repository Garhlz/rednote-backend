package com.szu.afternoon3.platform.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PostAuditLogVO {
    private String id;
    private Long operatorId;
    private String operatorName;
    private Integer auditStatus; // 1-通过 2-拒绝
    private String rejectReason;
    private LocalDateTime createdAt;
}