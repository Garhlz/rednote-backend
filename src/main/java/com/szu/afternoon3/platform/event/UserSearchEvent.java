package com.szu.afternoon3.platform.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class UserSearchEvent extends ApplicationEvent {
    private final Long userId;
    private final String keyword;

    public UserSearchEvent(Object source, Long userId, String keyword) {
        super(source);
        this.userId = userId;
        this.keyword = keyword;
    }
}