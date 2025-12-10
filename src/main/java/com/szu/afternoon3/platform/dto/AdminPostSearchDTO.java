package com.szu.afternoon3.platform.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class AdminPostSearchDTO {
    private String nickname;
    private String email;
    private LocalDate startTime;
    private LocalDate endTime;
    private List<String> tags;
    private String titleKeyword;
    
    private Integer page = 1;
    private Integer size = 10;
}
