package com.szu.afternoon3.platform.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PostTagSuggestDTO {
    // 标题非必填，因为用户可能还没想好标题
    private String title;

    @NotBlank(message = "内容不能为空")
    private String content;
}