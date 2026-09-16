package com.gxu.aihall.controller;

import com.gxu.aihall.common.Result;
import com.gxu.aihall.dto.FriendRequestVO;
import com.gxu.aihall.dto.PublicUserVO;
import com.gxu.aihall.entity.User;
import com.gxu.aihall.service.AuthService;
import com.gxu.aihall.service.FriendService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 好友接口：通过平台账号（学号）搜索并添加好友。
 */
@RestController
@RequestMapping("/api/friends")
public class FriendController {

    private final AuthService authService;
    private final FriendService friendService;

    public FriendController(AuthService authService, FriendService friendService) {
        this.authService = authService;
        this.friendService = friendService;
    }

    /** 好友列表 */
    @GetMapping
    public Result<List<PublicUserVO>> list(@RequestHeader(value = "Authorization", required = false) String token) {
        User user = authService.requireLogin(token, "未登录");
        return Result.success(friendService.listFriends(user.getId()));
    }

    /** 收到的好友申请 */
    @GetMapping("/requests")
    public Result<List<FriendRequestVO>> incoming(@RequestHeader(value = "Authorization", required = false) String token) {
        User user = authService.requireLogin(token, "未登录");
        return Result.success(friendService.incomingRequests(user.getId()));
    }

    /** 我发出的好友申请 */
    @GetMapping("/requests/sent")
    public Result<List<FriendRequestVO>> sent(@RequestHeader(value = "Authorization", required = false) String token) {
        User user = authService.requireLogin(token, "未登录");
        return Result.success(friendService.sentRequests(user.getId()));
    }

    /** 发起好友申请：body 传平台账号（account）或用户 id（userId） */
    @PostMapping("/requests")
    public Result<FriendRequestVO> send(@RequestHeader(value = "Authorization", required = false) String token,
                                        @RequestBody Map<String, Object> body) {
        User user = authService.requireLogin(token, "请先登录后再添加好友");
        String account = body.get("account") == null ? null : String.valueOf(body.get("account"));
        Long targetUserId = parseLong(body.get("userId"));
        return Result.success("好友申请已发送", friendService.sendRequest(user.getId(), account, targetUserId));
    }

    @PostMapping("/requests/{id}/accept")
    public Result<Void> accept(@RequestHeader(value = "Authorization", required = false) String token,
                               @PathVariable Long id) {
        User user = authService.requireLogin(token, "未登录");
        friendService.accept(user.getId(), id);
        return Result.success("已添加为好友", null);
    }

    @PostMapping("/requests/{id}/reject")
    public Result<Void> reject(@RequestHeader(value = "Authorization", required = false) String token,
                               @PathVariable Long id) {
        User user = authService.requireLogin(token, "未登录");
        friendService.reject(user.getId(), id);
        return Result.success("已拒绝该申请", null);
    }

    /** 删除好友 */
    @DeleteMapping("/{userId}")
    public Result<Void> remove(@RequestHeader(value = "Authorization", required = false) String token,
                               @PathVariable Long userId) {
        User user = authService.requireLogin(token, "未登录");
        friendService.removeFriend(user.getId(), userId);
        return Result.success("已删除好友", null);
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
}
