package com.szu.afternoon3.platform.listener;

import com.szu.afternoon3.platform.event.PostCreateEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class PostAuditListener {

    @Async // 异步执行，不卡前端接口
    @EventListener
    public void handlePostCreate(PostCreateEvent event) {
        // TODO: 这里写调用 AI 接口或者推送到管理端的逻辑
        // 1. 调用 阿里云/百度 内容安全 API
        // 2. 如果通过 -> update post set status = 1
        // 3. 如果不通过 -> update post set status = 2
    }
}
