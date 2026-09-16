package com.gxu.aihall.controller;

import com.gxu.aihall.common.Result;
import com.gxu.aihall.entity.User;
import com.gxu.aihall.service.AuthService;
import com.gxu.aihall.service.FileStorageService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 学生端文件上传接口（需登录）。
 * 注意：必须挂在 /api/upload 而不是 /api/admin/**，
 * 否则会被 AuthInterceptor 当作管理员接口拦截。
 */
@RestController
@RequestMapping("/api/upload")
public class StudentUploadController {

    private final AuthService authService;
    private final FileStorageService fileStorageService;

    public StudentUploadController(AuthService authService, FileStorageService fileStorageService) {
        this.authService = authService;
        this.fileStorageService = fileStorageService;
    }

    /** 上传商品 / 帖子 / 反馈配图 */
    @PostMapping("/image")
    public Result<Map<String, Object>> uploadImage(@RequestHeader(value = "Authorization", required = false) String token,
                                                   @RequestParam("file") MultipartFile file) throws Exception {
        User user = authService.requireLogin(token, "请先登录后再上传图片");
        // 保留登录校验：仅登录用户可上传
        if (user.getId() == null) {
            return Result.error("请先登录后再上传图片");
        }
        FileStorageService.StoredImage stored = fileStorageService.saveImageDetailed(file);
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("url", stored.url());
        data.put("thumbnailUrl", stored.thumbnailUrl());
        data.put("width", stored.width());
        data.put("height", stored.height());
        return Result.success(data);
    }
}
