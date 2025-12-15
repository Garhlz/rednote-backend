package com.szu.afternoon3.platform.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AdminPostVO {
    private String id;
    private Integer status; // 0:审核中, 1:发布, 2: 审核失败
    private String title;
    private String content;
    
    private Long userId;
    private String userEmail;
    private String userNickname;
    private String userAvatar;
    
    private LocalDateTime createTime;
    private List<String> tags;
    
    private Integer type;
    private List<String> resources;
    private String cover;

    private Integer coverWidth;

    private Integer coverHeight;
}
