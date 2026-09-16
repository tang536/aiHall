package com.gxu.aihall.controller;

import com.gxu.aihall.common.Result;
import com.gxu.aihall.entity.User;
import com.gxu.aihall.service.AuthService;
import com.gxu.aihall.service.PlatformBindService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 校园平台绑定：教务系统
 */
@RestController
@RequestMapping("/api/auth/bind")
public class BindController {

    private final AuthService authService;
    private final PlatformBindService platformBindService;

    public BindController(AuthService authService, PlatformBindService platformBindService) {
        this.authService = authService;
        this.platformBindService = platformBindService;
    }

    /**
     * 绑定教务系统（后端全自动模拟登录，无需验证码）
     */
    @PostMapping("/jwxt")
    public Result<Map<String, Object>> bindJwxt(@RequestHeader(value = "Authorization", required = false) String token,
                                                @RequestBody Map<String, String> body) {
        User user = authService.requireLogin(token, "未登录");
        try {
            PlatformBindService.BindResult result = platformBindService.jwxtBind(
                    user.getId(), body.get("username"), body.get("password"));
            Map<String, Object> data = new HashMap<>();
            data.put("bound", result.success);
            data.put("account", result.account);
            return Result.success(result.message, data);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.error("绑定失败，请稍后再试");
        }
    }

    /**
     * 查询教务系统的绑定状态
     */
    @GetMapping("/status")
    public Result<List<Map<String, Object>>> bindStatus(@RequestHeader(value = "Authorization", required = false) String token) {
        User user = authService.requireLogin(token, "未登录");
        return Result.success(platformBindService.bindStatus(user.getId()));
    }
}
