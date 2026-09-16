package com.gxu.aihall.controller;

import com.gxu.aihall.common.Result;
import com.gxu.aihall.service.AdminService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 公开统计接口（首页横幅等无需登录的场景使用，数据全部来自数据库真实统计）
 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final AdminService adminService;

    public StatsController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.success(adminService.getPublicStats());
    }
}
