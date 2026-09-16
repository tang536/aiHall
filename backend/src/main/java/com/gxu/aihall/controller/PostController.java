package com.gxu.aihall.controller;

import com.gxu.aihall.common.PageResult;
import com.gxu.aihall.common.Result;
import com.gxu.aihall.dto.PostReplyVO;
import com.gxu.aihall.dto.PostVO;
import com.gxu.aihall.entity.Post;
import com.gxu.aihall.entity.User;
import com.gxu.aihall.service.AuthService;
import com.gxu.aihall.service.PostService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 校园论坛帖子接口：发布 / 删除 / 列表 / 详情 / 回复 / 点赞。
 */
@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final AuthService authService;
    private final PostService postService;

    public PostController(AuthService authService, PostService postService) {
        this.authService = authService;
        this.postService = postService;
    }

    // ==================== 公开浏览 ====================

    @GetMapping
    public Result<PageResult<PostVO>> list(@RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String category,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size) {
        return Result.success(postService.list(keyword, category, page, size));
    }

    @GetMapping("/{id}")
    public Result<PostVO> detail(@PathVariable Long id) {
        PostVO vo = postService.getDetail(id);
        if (vo == null) return Result.error("帖子不存在或已被删除");
        return Result.success(vo);
    }

    // ==================== 需登录 ====================

    @PostMapping
    public Result<Post> publish(@RequestHeader(value = "Authorization", required = false) String token,
                                @RequestBody Post post) {
        User user = authService.requireLogin(token, "请先登录后再发帖");
        return Result.success("发布成功", postService.publish(post, user.getId()));
    }

    /** 删除自己的帖子 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@RequestHeader(value = "Authorization", required = false) String token,
                               @PathVariable Long id) {
        User user = authService.requireLogin(token, "未登录");
        postService.delete(id, user.getId());
        return Result.success("已删除", null);
    }

    @GetMapping("/mine")
    public Result<List<PostVO>> mine(@RequestHeader(value = "Authorization", required = false) String token) {
        User user = authService.requireLogin(token, "未登录");
        return Result.success(postService.listMine(user.getId()));
    }

    /**
     * 回复帖子 / 回复某条回复。
     * parentId 为空表示直接回复帖子（回复楼主），否则表示回复该条回复。
     */
    @PostMapping("/{id}/reply")
    public Result<PostReplyVO> reply(@RequestHeader(value = "Authorization", required = false) String token,
                                     @PathVariable Long id,
                                     @RequestBody Map<String, Object> body) {
        User user = authService.requireLogin(token, "请先登录后再回复");
        String content = body.get("content") == null ? null : String.valueOf(body.get("content"));
        Long parentId = parseLong(body.get("parentId"));
        return Result.success("回复成功", postService.reply(id, user.getId(), content, parentId));
    }

    /** 删除回复：回复者本人或楼主可删 */
    @DeleteMapping("/replies/{replyId}")
    public Result<Void> deleteReply(@RequestHeader(value = "Authorization", required = false) String token,
                                    @PathVariable Long replyId) {
        User user = authService.requireLogin(token, "未登录");
        postService.deleteReply(replyId, user.getId());
        return Result.success("已删除", null);
    }

    @PostMapping("/{id}/like")
    public Result<Map<String, Object>> like(@RequestHeader(value = "Authorization", required = false) String token,
                                            @PathVariable Long id) {
        authService.requireLogin(token, "请先登录后再点赞");
        Post post = postService.like(id);
        return Result.success(Map.of("likeCount", post.getLikeCount()));
    }

    private Long parseLong(Object raw) {
        if (raw == null) return null;
        String s = String.valueOf(raw).trim();
        if (s.isEmpty() || "null".equals(s)) return null;
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ==================== 收藏 ====================

    @PostMapping("/favorites/{postId}")
    public Result<Map<String, Object>> toggleFavorite(@RequestHeader(value = "Authorization", required = false) String token,
                                                        @PathVariable Long postId) {
        User user = authService.requireLogin(token, "请先登录");
        boolean favorited = postService.toggleFavorite(user.getId(), postId);
        return Result.success(favorited ? "已收藏" : "已取消收藏", Map.of("favorited", favorited));
    }

    @GetMapping("/favorites")
    public Result<List<PostVO>> myFavorites(@RequestHeader(value = "Authorization", required = false) String token) {
        User user = authService.requireLogin(token, "请先登录");
        return Result.success(postService.listFavorites(user.getId()));
    }

    @GetMapping("/favorites/check/{postId}")
    public Result<Map<String, Object>> checkFavorite(@RequestHeader(value = "Authorization", required = false) String token,
                                                       @PathVariable Long postId) {
        User user = authService.requireLogin(token, "请先登录");
        boolean favorited = postService.isFavorited(user.getId(), postId);
        return Result.success(Map.of("favorited", favorited));
    }
}
