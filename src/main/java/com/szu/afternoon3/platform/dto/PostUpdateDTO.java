package com.szu.afternoon3.platform.dto;

import lombok.Data;

import java.util.List;

@Data
public class PostUpdateDTO {
    // 允许部分更新，所以字段不加 @NotBlank，由 Service 层判空处理
    private String title;

    private String content;

    // 如果传了 images/videos，说明要覆盖原有的资源
    private List<String> images;

    private List<String> videos;

    private List<String> tags;
}