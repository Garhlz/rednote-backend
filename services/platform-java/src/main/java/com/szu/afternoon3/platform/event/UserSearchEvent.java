package com.szu.afternoon3.platform.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSearchEvent {
    private Long userId;
    private String keyword;
}