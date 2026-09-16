package com.gxu.aihall.controller;

import com.gxu.aihall.common.Result;
import com.gxu.aihall.service.SystemSettingService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 系统设置公开接口
 */
@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final SystemSettingService systemSettingService;

    public SystemController(SystemSettingService systemSettingService) {
        this.systemSettingService = systemSettingService;
    }

    /**
     * 验证专注模式密码
     */
    @PostMapping("/focus-password/verify")
    public Result<Boolean> verifyFocusPassword(@RequestBody Map<String, String> body) {
        String password = body.get("password");
        if (password == null || password.isEmpty()) {
            return Result.success(false);
        }
        return Result.success(systemSettingService.verifyFocusPassword(password));
    }

    /**
     * 检查是否已设置专注模式密码
     */
    @GetMapping("/focus-password/exists")
    public Result<Boolean> hasFocusPassword() {
        return Result.success(systemSettingService.hasFocusPassword());
    }

    /**
     * 首次设置专注模式密码（仅在未设置时可用）
     */
    @PostMapping("/focus-password/init")
    public Result<Void> initFocusPassword(@RequestBody Map<String, String> body) {
        if (systemSettingService.hasFocusPassword()) {
            return Result.error("密码已设置，如需修改请联系管理员");
        }
        String password = body.get("password");
        if (password == null || password.length() < 4) {
            return Result.error("密码至少4位");
        }
        systemSettingService.setFocusPassword(password);
        return Result.success();
    }
}
