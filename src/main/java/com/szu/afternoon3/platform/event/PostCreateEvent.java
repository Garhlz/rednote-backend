package com.szu.afternoon3.platform.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PostCreateEvent {
    private String postId;
    private String content;
    private String title;
    // TODO 如果是视频/图片审核，还需要 url
}