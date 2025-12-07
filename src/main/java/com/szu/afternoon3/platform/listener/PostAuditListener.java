package com.szu.afternoon3.platform.listener;

import com.szu.afternoon3.platform.event.PostCreateEvent;
import com.szu.afternoon3.platform.event.PostUpdateEvent;
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

    // 处理修改事件
    @Async
    @EventListener
    public void handlePostUpdate(PostUpdateEvent event) {
        // 逻辑同创建，需要重新审核内容安全
        // 审核通过 -> update post set status = 1
        // 审核不通过 -> update post set status = 2
    }
}
