package com.szu.afternoon3.platform.controller;

import com.szu.afternoon3.platform.common.Result;
import com.szu.afternoon3.platform.common.UserContext;
import com.szu.afternoon3.platform.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/message")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    /**
     * 获取未读消息数 (轮询接口)
     * 建议前端每 30s 调用一次，或者在 onShow 时调用
     */
    @GetMapping("/unread-count")
    public Result<Long> getUnreadCount() {
        Long userId = UserContext.getUserId();
        return Result.success(notificationService.countUnread(userId));
    }

    /**
     * 获取消息列表
     */
    @GetMapping("/notifications")
    public Result<Map<String, Object>> getList(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        Long userId = UserContext.getUserId();
        return Result.success(notificationService.getMyNotifications(userId, page, size));
    }

    /**
     * 一键已读 (清除小红点)
     * 进入消息页面或点击按钮时调用
     */
    @PostMapping("/read")
    public Result<Void> readAll() {
        Long userId = UserContext.getUserId();
        notificationService.markAllAsRead(userId);
        return Result.success();
    }
}