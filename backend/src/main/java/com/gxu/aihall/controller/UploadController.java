package com.gxu.aihall.controller;

import com.gxu.aihall.common.Result;
import com.gxu.aihall.service.FileStorageService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 文件上传接口（管理员端，需登录）
 * 目前用于地点照片上传，保存到项目运行目录下的 uploads/ 文件夹
 */
@RestController
@RequestMapping("/api/admin/upload")
public class UploadController {

    private final FileStorageService fileStorageService;

    public UploadController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @PostMapping("/image")
    public Result<Map<String, Object>> uploadImage(@RequestParam("file") MultipartFile file) throws Exception {
        FileStorageService.StoredImage stored = fileStorageService.saveImageDetailed(file);
        // 返回可访问的相对 URL（由 WebConfig 的 /uploads/** 静态映射提供）
        // thumbnailUrl 供列表页使用；没有缩略图时它会等于 url，前端无需分支处理
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("url", stored.url());
        data.put("thumbnailUrl", stored.thumbnailUrl());
        data.put("width", stored.width());
        data.put("height", stored.height());
        return Result.success(data);
    }
}
