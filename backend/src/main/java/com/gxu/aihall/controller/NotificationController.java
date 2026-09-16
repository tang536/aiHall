package com.gxu.aihall.controller;

import com.gxu.aihall.common.Result;
import com.gxu.aihall.entity.Notification;
import com.gxu.aihall.service.NotificationService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public Result<List<Notification>> getAll(@RequestParam(required = false) String category,
                                              @RequestParam(required = false) String keyword) {
        if (StringUtils.hasText(keyword)) {
            return Result.success(notificationService.search(keyword));
        }
        if (StringUtils.hasText(category)) {
            return Result.success(notificationService.getByCategory(category));
        }
        return Result.success(notificationService.getAll());
    }

    @GetMapping("/{id}")
    public Result<Notification> getById(@PathVariable Long id) {
        Notification n = notificationService.getById(id);
        if (n == null) return Result.error("通知不存在");
        return Result.success(n);
    }

    @GetMapping("/emergency")
    public Result<List<Notification>> getEmergency() {
        return Result.success(notificationService.getEmergencyNotifications());
    }
}
