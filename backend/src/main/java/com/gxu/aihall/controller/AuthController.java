package com.gxu.aihall.controller;

import com.gxu.aihall.common.Result;
import com.gxu.aihall.doc.ApiDoc;
import com.gxu.aihall.dto.LoginRequest;
import com.gxu.aihall.dto.LoginResponse;
import com.gxu.aihall.dto.RegisterRequest;
import com.gxu.aihall.entity.User;
import com.gxu.aihall.service.AuthService;
import com.gxu.aihall.service.CaptchaService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;

    public AuthController(AuthService authService, CaptchaService captchaService) {
        this.authService = authService;
        this.captchaService = captchaService;
    }

    /**
     * 获取图形验证码（base64 图片 + captchaId），5 分钟有效，一次性使用
     */
    @ApiDoc("获取图形验证码（返回 captchaId 与 base64 图片，5 分钟有效、一次性）")
    @GetMapping("/captcha")
    public Result<Map<String, String>> captcha() {
        return Result.success(captchaService.generate());
    }

    @ApiDoc("平台账号密码登录（需先取验证码；单设备登录，新登录会顶掉旧令牌）")
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        // 业务失败一律抛 BizException，由 GlobalExceptionHandler 统一带上语义化错误码（400/403/429）
        return Result.success(authService.login(request));
    }

    /**
     * 学校账号登录：用户已绑定教务系统后，可用教务系统学号 + 密码直接登录本平台。
     * 采用离线校验（比对绑定记录中加密保存的凭证），不依赖校园网可达。
     */
    @ApiDoc("教务（学校）账号登录：登录的是被该教务号绑定的平台账号本身")
    @PostMapping("/login/school")
    public Result<LoginResponse> schoolLogin(@RequestBody LoginRequest request) {
        return Result.success("登录成功", authService.schoolLogin(request));
    }

    @ApiDoc("学生注册（同一 IP 有注册冷却）")
    @PostMapping("/register")
    public Result<LoginResponse> register(@RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        return Result.success("注册成功", authService.register(request, resolveClientIp(httpRequest)));
    }

    /**
     * 解析客户端真实 IP（兼容反向代理场景）
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    @ApiDoc("退出登录：当前 jti 进黑名单，立即失效")
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String token) {
        if (token != null) {
            authService.logout(token.replace("Bearer ", ""));
        }
        return Result.success();
    }

    @GetMapping("/userinfo")
    public Result<User> getUserInfo(@RequestHeader(value = "Authorization", required = false) String token) {
        User user = authService.requireLogin(token, "未登录");
        user.setPassword(null);
        return Result.success(user);
    }

    /**
     * 修改个人资料（学号、角色不可修改）
     */
    @PutMapping("/profile")
    public Result<User> updateProfile(@RequestHeader(value = "Authorization", required = false) String token, @RequestBody User profile) {
        User user = authService.requireLogin(token, "未登录");
        User updated = authService.updateProfile(user.getId(), profile);
        updated.setPassword(null);
        return Result.success("资料已更新", updated);
    }

    /**
     * 修改密码：校验原密码后更新，成功后建议重新登录
     */
    @PutMapping("/password")
    public Result<Void> updatePassword(@RequestHeader(value = "Authorization", required = false) String token,
                                       @RequestBody Map<String, String> body) {
        User user = authService.requireLogin(token, "未登录");
        authService.updatePassword(user.getId(), body.get("oldPassword"), body.get("newPassword"));
        return Result.success("密码已修改", null);
    }
}
