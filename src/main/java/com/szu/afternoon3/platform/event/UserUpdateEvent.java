package com.szu.afternoon3.platform.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

// UserUpdateEvent.java
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class UserUpdateEvent {
    private Long userId;
    private String newNickname;
    private String newAvatar;
}