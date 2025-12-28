package com.szu.afternoon3.platform.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ApiLogVO {
    private String id;
    private String logType;   // USER_OPER / ADMIN_OPER
    private String module;    // 模块名称
    private String description;
    private String bizId;     // 业务ID
    
    private Long userId;
    private String username;
    private String role;
    
    private String method;
    private String uri;
    private String ip;
    private Integer status;
    private String errorMsg;
    private Long timeCost;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt; 
}