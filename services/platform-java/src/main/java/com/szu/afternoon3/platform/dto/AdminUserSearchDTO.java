package com.szu.afternoon3.platform.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class AdminUserSearchDTO {
    private String nickname;
    private String email;
    private LocalDate startTime;
    private LocalDate endTime;
    
    private Integer page = 1;
    private Integer size = 10;
}
