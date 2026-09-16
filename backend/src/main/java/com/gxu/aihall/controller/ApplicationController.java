package com.gxu.aihall.controller;

import com.gxu.aihall.common.Result;
import com.gxu.aihall.dto.ApplicationRequest;
import com.gxu.aihall.entity.Application;
import com.gxu.aihall.service.ApplicationService;
import com.gxu.aihall.service.AuthService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService applicationService;
    private final AuthService authService;

    public ApplicationController(ApplicationService applicationService, AuthService authService) {
        this.applicationService = applicationService;
        this.authService = authService;
    }

    @PostMapping("/submit")
    public Result<Application> submit(@RequestBody ApplicationRequest request,
                                       @RequestHeader("Authorization") String token) {
        var user = authService.requireLogin(token, "未登录");
        Application app = applicationService.submit(request, user.getId());
        return Result.success("申请提交成功，申请编号：" + app.getApplicationNo(), app);
    }

    @GetMapping("/my")
    public Result<List<Application>> getMyApplications(@RequestHeader("Authorization") String token) {
        var user = authService.requireLogin(token, "未登录");
        return Result.success(applicationService.getByStudentId(user.getId()));
    }

    @GetMapping("/{appNo}")
    public Result<Application> getByAppNo(@PathVariable String appNo) {
        Application app = applicationService.getByAppNo(appNo);
        if (app == null) return Result.error("申请编号不存在");
        return Result.success(app);
    }
}
