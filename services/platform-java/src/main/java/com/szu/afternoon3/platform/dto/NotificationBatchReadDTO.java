package com.szu.afternoon3.platform.dto;

import lombok.Data;
import java.util.List;

@Data
public class NotificationBatchReadDTO {
    // 前端传来的 ID 列表
    private List<String> ids;
}