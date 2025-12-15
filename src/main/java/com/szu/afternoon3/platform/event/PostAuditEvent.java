package com.szu.afternoon3.platform.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PostAuditEvent implements Serializable {
    private String postId;
    private Long authorId;   // 作者ID (接收通知的人)
    private String postTitle; // 帖子标题 (用于通知展示)
    private Integer status;  // 1=通过, 2=拒绝
    private String reason;   // 拒绝理由
}