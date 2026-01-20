package com.szu.afternoon3.platform.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PostDeleteEvent {
    private String postId;
    private Long operatorId;    // 操作人ID (谁点的删除)

    private String operatorName;
    // === 【新增字段】用于生成通知和日志 ===
    private Long authorId;      // 帖子作者ID (给谁发通知)
    private String postTitle;   // 帖子标题 (通知里展示)
    private String reason;      // 删除原因 (如果是管理员删除)

    // 必须要手动映射，否则出错！
    @JsonProperty("adminOp")
    private boolean isAdminOp;  // 是否是管理员操作
}