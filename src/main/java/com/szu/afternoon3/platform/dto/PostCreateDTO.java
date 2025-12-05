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

    /**
     * 0:图文, 1:视频
     */
    @NotNull(message = "帖子类型不能为空")
    private Integer type;

    /**
     * 图片URL列表 (type=0时必填)
     */
    private List<String> images;

    /**
     * 视频URL列表 (type=1时必填)
     */
    private List<String> videos;

    /**
     * 标签列表
     */
    private List<String> tags;
}