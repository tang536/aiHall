package com.gxu.aihall.controller;

import com.gxu.aihall.common.Result;
import com.gxu.aihall.entity.PostCategory;
import com.gxu.aihall.service.PostCategoryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 论坛帖子分类管理接口（管理员端，/api/admin/** 由 AuthInterceptor 强制 ADMIN 鉴权）
 */
@RestController
@RequestMapping("/api/admin/post-categories")
public class AdminPostCategoryController {

    private final PostCategoryService categoryService;

    public AdminPostCategoryController(PostCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public Result<List<PostCategory>> listAll() {
        return Result.success(categoryService.listAll());
    }

    @PostMapping
    public Result<PostCategory> create(@RequestBody Map<String, Object> body) {
        String code = (String) body.get("code");
        String name = (String) body.get("name");
        Integer sort = body.get("sort") != null ? ((Number) body.get("sort")).intValue() : null;
        return Result.success("分类创建成功", categoryService.create(code, name, sort));
    }

    @PutMapping("/{id}")
    public Result<PostCategory> update(@PathVariable Long id,
                                        @RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        Integer sort = body.get("sort") != null ? ((Number) body.get("sort")).intValue() : null;
        Integer status = body.get("status") != null ? ((Number) body.get("status")).intValue() : null;
        return Result.success("分类更新成功", categoryService.update(id, name, sort, status));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.success("分类已删除", null);
    }
}
