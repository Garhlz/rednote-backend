package com.szu.afternoon3.platform.dto;

import lombok.Data;

@Data
public class FriendSearchDTO {
    private Integer page = 1;      // 默认第1页
    private Integer size = 20;     // 默认20条
    private String nickname;       // 搜索关键词 (选填)
}