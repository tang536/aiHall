package com.gxu.aihall.controller;

import com.gxu.aihall.common.Result;
import com.gxu.aihall.entity.PostCategory;
import com.gxu.aihall.service.PostCategoryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 论坛帖子分类公开接口（学生端发布/筛选用）
 */
@RestController
@RequestMapping("/api/post-categories")
public class PostCategoryController {

    private final PostCategoryService categoryService;

    public PostCategoryController(PostCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /** 获取启用的分类列表 */
    @GetMapping
    public Result<List<PostCategory>> list() {
        return Result.success(categoryService.listEnabled());
    }
}
