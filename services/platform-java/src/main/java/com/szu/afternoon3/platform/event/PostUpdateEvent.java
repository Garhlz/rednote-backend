package com.szu.afternoon3.platform.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PostUpdateEvent {
    private String postId;
    private String title;
    private String content;
    // 后续监听器可据此调用 AI 审核或更新搜索索引
}