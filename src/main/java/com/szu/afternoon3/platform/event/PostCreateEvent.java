package com.szu.afternoon3.platform.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PostCreateEvent {
    private String id;
    private String content;
    private String title;
    // TODO 如果是视频/图片审核，还需要 url
    private List<String> images;
    private String video;

    // 【新增】 ES 需要的字段，避免 Listener 回查 DB
    private Long userId;
    private List<String> tags;
    private Integer type;
    private String cover;
    private Integer coverWidth;
    private Integer coverHeight;

    private String userNickname;
    private String userAvatar;
}