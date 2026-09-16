package com.gxu.aihall.controller;

import com.gxu.aihall.common.Result;
import com.gxu.aihall.entity.Feedback;
import com.gxu.aihall.entity.User;
import com.gxu.aihall.service.AuthService;
import com.gxu.aihall.service.FeedbackService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 学生对平台的反馈通道（管理员侧在 AdminController 中接收处理）
 */
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final AuthService authService;
    private final FeedbackService feedbackService;

    public FeedbackController(AuthService authService, FeedbackService feedbackService) {
        this.authService = authService;
        this.feedbackService = feedbackService;
    }

    /** 提交反馈 */
    @PostMapping
    public Result<Feedback> submit(@RequestHeader(value = "Authorization", required = false) String token,
                                   @RequestBody Feedback form) {
        User user = authService.requireLogin(token, "请先登录后再提交反馈");
        return Result.success("反馈已提交，我们会尽快处理", feedbackService.submit(form, user.getId()));
    }

    /** 我的反馈（含管理员的回复与处理状态） */
    @GetMapping("/mine")
    public Result<List<Feedback>> mine(@RequestHeader(value = "Authorization", required = false) String token) {
        User user = authService.requireLogin(token, "未登录");
        return Result.success(feedbackService.listMine(user.getId()));
    }

    @GetMapping("/{id}")
    public Result<Feedback> detail(@RequestHeader(value = "Authorization", required = false) String token,
                                   @PathVariable Long id) {
        User user = authService.requireLogin(token, "未登录");
        Feedback fb = feedbackService.getById(id);
        if (fb == null) return Result.error("反馈不存在");
        if (!fb.getUserId().equals(user.getId())) return Result.error(403, "无权查看该反馈");
        return Result.success(fb);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@RequestHeader(value = "Authorization", required = false) String token,
                               @PathVariable Long id) {
        User user = authService.requireLogin(token, "未登录");
        feedbackService.delete(id, user.getId());
        return Result.success("已删除", null);
    }
}
