package com.szu.afternoon3.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class PostCreateDTO {

    @NotBlank(message = "标题不能为空")
    private String title;

    @NotBlank(message = "内容不能为空")
    private String content;

    @NotNull(message = "帖子类型不能为空")
    private Integer type; // 0:图文, 1:视频, 2:纯文字

    // 统一用 images 接收图片（图文、纯文字背景图）
    private List<String> images;

    // 单独接收视频（只有 type=1 时传，且只是一个字符串）
    private String video;

    /**
     * 标签列表
     */
    private List<String> tags;
}