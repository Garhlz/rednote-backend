package com.szu.afternoon3.platform.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
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

    /**
     * pattern: 指定输出格式
     * timezone: 指定时区 (GMT+8 是北京时间)，防止时间差8小时
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;
    private List<String> tags;
    
    private Integer type;
    private List<String> resources;
    private String cover;

    private Integer coverWidth;

    private Integer coverHeight;

    // 统计数据
    private Integer viewCount = 0;
    private Integer likeCount = 0;
    private Integer collectCount = 0;
    private Integer commentCount = 0;

}
