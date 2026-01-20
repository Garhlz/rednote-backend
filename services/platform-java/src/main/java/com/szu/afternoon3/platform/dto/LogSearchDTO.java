package com.szu.afternoon3.platform.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class LogSearchDTO {
    /**
     * 操作人ID (精确查询)
     */
    private Long userId;

    /**
     * 关键词 (支持模糊搜索: 描述 description 或 接口路径 uri)
     */
    private String keyword;

    /**
     * 状态码 (例如 200 或 500)
     */
    private Integer status;

    /**
     * 业务对象ID (精确查询，例如查某个帖子的所有操作记录)
     */
    private String bizId;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    /**
     * 分页参数
     */
    private Integer page = 1;
    private Integer size = 20;

    // 【新增】支持按昵称模糊搜 (更人性化)
    private String username;

    // 【新增】支持按模块精确筛 (对应产品说的“操作类型”)
    private String module;
}