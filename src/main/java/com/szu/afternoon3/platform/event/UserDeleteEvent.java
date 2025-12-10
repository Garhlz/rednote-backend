package com.szu.afternoon3.platform.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class UserDeleteEvent extends ApplicationEvent {
    private final Long userId;

    public UserDeleteEvent(Object source, Long userId) {
        super(source);
        this.userId = userId;
    }
}