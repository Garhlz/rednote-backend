package com.szu.afternoon3.platform.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

// UserUpdateEvent.java
@Getter
@AllArgsConstructor
public class UserUpdateEvent {
    private Long userId;
    private String newNickname;
    private String newAvatar;
}