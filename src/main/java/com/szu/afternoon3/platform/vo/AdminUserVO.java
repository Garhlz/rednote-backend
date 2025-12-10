package com.szu.afternoon3.platform.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AdminUserVO {
    private Long id;
    private String email;
    private String nickname;
    private LocalDateTime registerTime;
    
    // 统计数据
    private Long postCount;
    private Long fanCount;
    private Long likeCount;
    private Long followCount;
    private Double avgScore;
}
