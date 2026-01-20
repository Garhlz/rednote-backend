package com.szu.afternoon3.platform.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class PostTagSuggestDTO {
    // 标题非必填，因为用户可能还没想好标题
    private String title;

    @NotBlank(message = "内容不能为空")
    private String content;

    // 用 images 接收图片（图文、纯文字背景图）
    private List<String> images;

    // 单独接收视频
    private String video;
}