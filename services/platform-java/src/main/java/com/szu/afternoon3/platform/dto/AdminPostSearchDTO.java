package com.szu.afternoon3.platform.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class AdminPostSearchDTO {
    /**
     * Tab 类型:
     * 0 或 null = 全部 (执行自定义排序)
     * 1 = 待审核 (status=0)
     * 2 = 已通过 (status=1)
     * 3 = 已拒绝 (status=2)
     * 4 = 回收站 (isDeleted=1)
     */
    private Integer tab = 0;

    private String nickname;
    private String email;
    private LocalDate startTime;
    private LocalDate endTime;
    private List<String> tags;
    private String titleKeyword;

    private Integer page = 1;
    private Integer size = 10;
}