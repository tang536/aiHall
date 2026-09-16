package com.gxu.aihall.controller;

import com.gxu.aihall.common.Result;
import com.gxu.aihall.dto.PublicUserVO;
import com.gxu.aihall.entity.User;
import com.gxu.aihall.service.AuthService;
import com.gxu.aihall.service.FriendService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户查询接口。
 * 出于隐私保护，对外只返回 PublicUserVO（脱敏后的展示名、头像、学院/专业/年级与脱敏学号），
 * 手机号、邮箱、完整学号一律不返回。
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final AuthService authService;
    private final FriendService friendService;

    public UserController(AuthService authService, FriendService friendService) {
        this.authService = authService;
        this.friendService = friendService;
    }

    /** 按平台账号（学号）或姓名搜索用户 */
    @GetMapping("/search")
    public Result<List<PublicUserVO>> search(@RequestHeader(value = "Authorization", required = false) String token,
                                             @RequestParam String account) {
        User user = authService.requireLogin(token, "请先登录后再搜索用户");
        return Result.success(friendService.search(user.getId(), account));
    }

    /** 用户公开资料（脱敏） */
    @GetMapping("/{id}")
    public Result<PublicUserVO> profile(@RequestHeader(value = "Authorization", required = false) String token,
                                        @PathVariable Long id) {
        User user = authService.requireLogin(token, "未登录");
        PublicUserVO vo = friendService.profile(user.getId(), id);
        if (vo == null) return Result.error("用户不存在");
        return Result.success(vo);
    }
}
