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

    private long pendingPostCount;  // 待审核的帖子数(status=0)
    private Long passedPostCount;   // 审核通过的帖子数 (status=1)
    private Long rejectedPostCount; // 审核拒绝的帖子数 (status=2)


}
