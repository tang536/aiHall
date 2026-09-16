package com.gxu.aihall.controller;

import com.gxu.aihall.common.Result;
import com.gxu.aihall.entity.User;
import com.gxu.aihall.entity.UserNotification;
import com.gxu.aihall.service.AuthService;
import com.gxu.aihall.service.UserNotificationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 个人通知接口：好友申请、商品被购买等
 */
@RestController
@RequestMapping("/api/user-notifications")
public class UserNotificationController {

    private final AuthService authService;
    private final UserNotificationService notificationService;

    public UserNotificationController(AuthService authService, UserNotificationService notificationService) {
        this.authService = authService;
        this.notificationService = notificationService;
    }

    @GetMapping
    public Result<List<UserNotification>> list(@RequestHeader(value = "Authorization", required = false) String token) {
        User user = authService.requireLogin(token, "请先登录");
        return Result.success(notificationService.listByUser(user.getId()));
    }

    @GetMapping("/unread-count")
    public Result<Map<String, Object>> unreadCount(@RequestHeader(value = "Authorization", required = false) String token) {
        User user = authService.requireLogin(token, "请先登录");
        long count = notificationService.countUnread(user.getId());
        return Result.success(Map.of("count", count));
    }

    @PutMapping("/{id}/read")
    public Result<Void> markRead(@RequestHeader(value = "Authorization", required = false) String token,
                                  @PathVariable Long id) {
        User user = authService.requireLogin(token, "请先登录");
        notificationService.markRead(id, user.getId());
        return Result.success("已标记为已读", null);
    }

    @PutMapping("/read-all")
    public Result<Void> markAllRead(@RequestHeader(value = "Authorization", required = false) String token) {
        User user = authService.requireLogin(token, "请先登录");
        notificationService.markAllRead(user.getId());
        return Result.success("已全部标记为已读", null);
    }
}
