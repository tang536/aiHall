package com.gxu.aihall.controller;

import com.gxu.aihall.common.Result;
import com.gxu.aihall.entity.LostItem;
import com.gxu.aihall.service.AuthService;
import com.gxu.aihall.service.LostFoundService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/lost-found")
public class LostFoundController {

    private final LostFoundService lostFoundService;
    private final AuthService authService;

    public LostFoundController(LostFoundService lostFoundService, AuthService authService) {
        this.lostFoundService = lostFoundService;
        this.authService = authService;
    }

    @GetMapping
    public Result<List<LostItem>> getAll(@RequestParam(required = false) String type) {
        if (type != null && !type.isEmpty()) {
            return Result.success(lostFoundService.getByType(type));
        }
        return Result.success(lostFoundService.getAll());
    }

    @GetMapping("/{id}")
    public Result<LostItem> getById(@PathVariable Long id) {
        LostItem item = lostFoundService.getById(id);
        if (item == null) return Result.error("信息不存在");
        return Result.success(item);
    }

    @PostMapping
    public Result<LostItem> publish(@RequestBody LostItem item,
                                     @RequestHeader(value = "Authorization", required = false) String token) {
        if (token != null) {
            var user = authService.getUserByBearerToken(token);
            if (user != null) {
                item.setPublisherId(user.getId());
                if (item.getContactName() == null) item.setContactName(user.getRealName());
                if (item.getContactPhone() == null) item.setContactPhone(user.getPhone());
            }
        }
        LostItem saved = lostFoundService.publish(item);
        return Result.success("发布成功", saved);
    }

    @PostMapping("/{id}/claim")
    public Result<Void> claim(@PathVariable Long id,
                              @RequestHeader(value = "Authorization", required = false) String token) {
        // 鉴权：必须登录，且只能是发布者本人或管理员才能标记已认领/已找回
        var user = authService.requireLogin(token, "请先登录后再操作");
        LostItem item = lostFoundService.getById(id);
        if (item == null) return Result.error("信息不存在");
        boolean isPublisher = item.getPublisherId() != null && item.getPublisherId().equals(user.getId());
        boolean isAdmin = "ADMIN".equalsIgnoreCase(user.getRole());
        if (!isPublisher && !isAdmin) {
            return Result.error(403, "仅发布者本人或管理员可标记已认领/已找回");
        }
        lostFoundService.claim(id);
        return Result.success();
    }

    @PostMapping("/match")
    public Result<List<LostItem>> smartMatch(@RequestBody LostItem item) {
        return Result.success(lostFoundService.smartMatch(item));
    }
}
