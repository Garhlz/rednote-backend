package com.szu.afternoon3.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class PostUpdateDTO {
    // 允许部分更新，所以字段不加 @NotBlank，由 Service 层判空处理
    private String title;

    private String content;

    // --- 资源互斥 ---

    // 1. 如果是图文帖(type=0) 或 纯文字帖(type=2)，传这个
    private List<String> images;

    // 2. 如果是视频帖(type=1)，传这个 (单视频 URL)
    private String video;

    // ----------------

    /**
     * 标签列表
     */
    private List<String> tags;

    @NotBlank(message = "封面宽度不能为空")
    private Integer coverWidth;

    @NotBlank(message = "封面高度不能为空")
    private Integer coverHeight;
}