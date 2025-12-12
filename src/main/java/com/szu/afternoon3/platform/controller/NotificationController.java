package com.szu.afternoon3.platform.controller;

import com.szu.afternoon3.platform.annotation.OperationLog;
import com.szu.afternoon3.platform.common.Result;
import com.szu.afternoon3.platform.common.UserContext;
import com.szu.afternoon3.platform.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 通知消息控制器
 * 处理系统通知的获取和状态更新
 */
@RestController
@RequestMapping("/api/message")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    /**
     * 获取未读消息数
     * @return 未读数量
     */
    @GetMapping("/unread-count")
    // @OperationLog(module = "消息模块", description = "轮询未读数") // 轮询接口建议不记录日志，防刷屏
    public Result<Long> getUnreadCount() {
        Long userId = UserContext.getUserId();
        return Result.success(notificationService.countUnread(userId));
    }

    /**
     * 获取消息列表
     * @return 消息分页列表
     */
    @GetMapping("/notifications")
    @OperationLog(module = "消息模块", description = "查看消息列表")
    public Result<Map<String, Object>> getList(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        Long userId = UserContext.getUserId();
        return Result.success(notificationService.getMyNotifications(userId, page, size));
    }

    /**
     * 一键已读
     */
    @PostMapping("/read")
    @OperationLog(module = "消息模块", description = "消息一键已读")
    public Result<Void> readAll() {
        Long userId = UserContext.getUserId();
        notificationService.markAllAsRead(userId);
        return Result.success();
    }
}