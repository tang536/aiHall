package com.gxu.aihall.service;

import com.gxu.aihall.common.BizException;
import com.gxu.aihall.dto.LoginRequest;
import com.gxu.aihall.dto.LoginResponse;
import com.gxu.aihall.dto.RegisterRequest;
import com.gxu.aihall.entity.User;
import com.gxu.aihall.entity.UserBinding;
import com.gxu.aihall.repository.UserBindingRepository;
import com.gxu.aihall.repository.UserRepository;
import com.gxu.aihall.security.AuthStore;
import com.gxu.aihall.security.IssuedToken;
import com.gxu.aihall.security.JwtPayload;
import com.gxu.aihall.security.JwtService;
import com.gxu.aihall.util.AesUtil;
import com.gxu.aihall.util.PasswordUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 认证服务。
 * <p>登录态采用 <b>JWT（无状态） + AuthStore（有状态）</b> 的组合：
 * 令牌自包含身份与有效期，服务端只在「单设备登录、主动登出、频控」这几件事上
 * 才读写共享存储，因此既能水平扩展，又保留了被顶下线的语义。
 */
@Service
public class AuthService {

    /** 已绑定教务系统时，学校账号在本平台的登录标识 */
    private static final String SCHOOL_PLATFORM = "JWXT";

    private final UserRepository userRepository;
    private final CaptchaService captchaService;
    private final UserBindingRepository userBindingRepository;
    private final JwtService jwtService;
    private final AuthStore authStore;

    /** 与绑定服务共用同一密钥，用于解密已保存的教务系统密码 */
    @Value("${app.jwt.secret}")
    private String encryptSecret;

    @Value("${app.security.login-max-failures:10}")
    private int loginMaxFailures;

    @Value("${app.security.login-lock-minutes:10}")
    private int loginLockMinutes;

    @Value("${app.security.register-cooldown-seconds:60}")
    private int registerCooldownSeconds;

    /** 剩余有效期少于该时长时自动续期（滑动续期窗口） */
    @Value("${app.jwt.renew-before-minutes:120}")
    private long renewBeforeMinutes;
    private Duration renewBefore = Duration.ofMinutes(120);

    @jakarta.annotation.PostConstruct
    void initRenewWindow() {
        this.renewBefore = Duration.ofMinutes(Math.max(1, renewBeforeMinutes));
    }

    private static final String LOGIN_FAIL_KEY = "login:fail:";
    private static final String REGISTER_KEY = "register:ip:";

    public AuthService(UserRepository userRepository, CaptchaService captchaService,
                       UserBindingRepository userBindingRepository,
                       JwtService jwtService, AuthStore authStore) {
        this.userRepository = userRepository;
        this.captchaService = captchaService;
        this.userBindingRepository = userBindingRepository;
        this.jwtService = jwtService;
        this.authStore = authStore;
    }

    public LoginResponse login(LoginRequest request) {
        // 1. 校验图形验证码（一次性）
        if (!captchaService.validate(request.getCaptchaId(), request.getCaptchaCode())) {
            throw new BizException(400, "验证码错误或已过期，请刷新后重试");
        }

        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        assertLoginNotLocked(username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BizException(400, "用户不存在"));

        // 2. 校验密码（支持 BCrypt；兼容历史明文数据，校验通过后自动升级为加密存储）
        if (!verifyPassword(request.getPassword(), user)) {
            recordLoginFailure(username);
            throw new BizException(400, "密码错误");
        }

        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(403, "账号已被禁用");
        }

        authStore.reset(LOGIN_FAIL_KEY + username);
        // 3. 签发新令牌，并踢掉旧设备上的会话（同一账号只允许一个活跃登录）
        return createSession(user);
    }

    /**
     * 学校账号登录：用户已绑定教务系统后，可直接用教务系统的学号 + 密码登录本平台。
     * 采用「离线校验」：不实时请求教务系统，而是比对绑定记录中 AES 加密保存的密码，
     * 因此校外 / 断网环境同样可用。校验通过后为绑定的平台账号签发 token。
     */
    public LoginResponse schoolLogin(LoginRequest request) {
        if (!captchaService.validate(request.getCaptchaId(), request.getCaptchaCode())) {
            throw new BizException(400, "验证码错误或已过期，请刷新后重试");
        }
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            throw new BizException(400, "请输入学校账号（学号）");
        }
        if (request.getPassword() == null || request.getPassword().isEmpty()) {
            throw new BizException(400, "请输入学校账号密码");
        }

        String schoolAccount = request.getUsername().trim();
        String failKey = LOGIN_FAIL_KEY + "school:" + schoolAccount;
        if (authStore.count(failKey) >= loginMaxFailures) {
            throw new BizException(429, "失败次数过多，账号已临时锁定，请 " + loginLockMinutes + " 分钟后再试");
        }

        UserBinding binding = userBindingRepository
                .findFirstByPlatformAndBindAccountAndStatus(SCHOOL_PLATFORM, schoolAccount, 1)
                .orElseThrow(() -> new BizException(400,
                        "该学校账号尚未绑定，请先用平台账号登录，并在「个人资料」中绑定教务系统"));

        if (binding.getEncryptedPassword() == null || binding.getEncryptedPassword().isBlank()) {
            throw new BizException(400, "该绑定记录缺少凭证，请在「个人资料」中重新绑定教务系统");
        }

        String storedPassword;
        try {
            storedPassword = AesUtil.decrypt(binding.getEncryptedPassword(), encryptSecret);
        } catch (Exception e) {
            throw new BizException(400, "学校账号凭证解析失败，请重新绑定教务系统");
        }
        if (!request.getPassword().equals(storedPassword)) {
            authStore.increment(failKey, Duration.ofMinutes(loginLockMinutes));
            throw new BizException(400, "学校账号或密码错误");
        }

        User user = userRepository.findById(binding.getUserId())
                .orElseThrow(() -> new BizException(400, "绑定的平台账号不存在"));
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(403, "账号已被禁用");
        }
        authStore.reset(failKey);
        return createSession(user);
    }

    /**
     * 为用户签发会话（登记为唯一活跃令牌）并组装登录响应。
     * 普通登录、注册、学校账号登录共用此逻辑。
     */
    public LoginResponse createSession(User user) {
        IssuedToken issued = jwtService.issue(user);
        // 登记为唯一活跃会话：新登录会把该用户旧令牌顶下线
        authStore.bindActiveSession(user.getId(), issued.tokenId(), issued.ttl());

        LoginResponse resp = new LoginResponse();
        resp.setToken(issued.token());
        resp.setUserId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setRealName(user.getRealName());
        resp.setRole(user.getRole());
        resp.setCollege(user.getCollege());
        resp.setBalance(user.getBalance() == null ? java.math.BigDecimal.ZERO : user.getBalance());
        return resp;
    }

    /**
     * 登出：清除活跃会话，并把该令牌的 jti 加入黑名单（保留到令牌自然过期），
     * 这样即使令牌还在有效期内也无法再次使用。
     */
    public void logout(String token) {
        JwtPayload payload = jwtService.parse(token);
        if (payload == null) return;
        if (Objects.equals(payload.tokenId(), authStore.getActiveSession(payload.userId()))) {
            authStore.clearActiveSession(payload.userId());
        }
        Duration remain = remainingTtl(payload);
        if (!remain.isNegative() && !remain.isZero()) {
            authStore.blacklist(payload.tokenId(), remain);
        }
    }

    /**
     * 由令牌解析登录用户。
     * 校验顺序：签名与有效期（JWT 自校验） → 是否已登出（黑名单）
     * → 是否被新登录顶下线（非当前活跃会话） → 账号是否仍启用。
     */
    public User getUserByToken(String token) {
        JwtPayload payload = jwtService.parse(token);
        if (payload == null) return null;
        if (authStore.isBlacklisted(payload.tokenId())) return null;

        String activeTokenId = authStore.getActiveSession(payload.userId());
        // activeTokenId 为空说明该用户没有活跃登录记录（例如重启后内存态丢失），此时信任令牌本身
        if (activeTokenId != null && !Objects.equals(activeTokenId, payload.tokenId())) return null;

        User user = userRepository.findById(payload.userId()).orElse(null);
        // 账号被封禁后立即失效：即使旧令牌仍在有效期内也不放行
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            if (user != null) {
                logout(token);
            }
            return null;
        }
        return user;
    }

    /**
     * 滑动续期：令牌剩余有效期低于阈值时，用<b>同一个 jti</b> 换发一枚新令牌。
     * <p>解决的是「24h 一到就被踢出去」的问题：只要用户还在用，登录态就一直延续，
     * 不需要在到期瞬间重新输密码。返回 null 表示「还不需要续期」或「不该续期」。
     * <p>几处刻意的克制：
     * <ul>
     *   <li>只有<b>合法且仍是当前活跃会话</b>的令牌才续 —— 已被顶下线/已登出的不能借续期复活；</li>
     *   <li>不换 jti（见 {@link JwtService#renew}），避免并发请求被判成「被迫下线」；</li>
     *   <li>续期的同时把活跃会话 TTL 一并延长，否则会话先过期、令牌还在，语义不一致。</li>
     * </ul>
     */
    public String maybeRenew(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) return null;
        JwtPayload payload = jwtService.parse(bearerToken.replace("Bearer ", ""));
        if (payload == null || payload.expiresAt() == null) return null;

        Duration remaining = Duration.between(Instant.now(), payload.expiresAt());
        if (remaining.compareTo(renewBefore) > 0) return null;   // 还没到续期窗口
        if (authStore.isBlacklisted(payload.tokenId())) return null;

        String activeTokenId = authStore.getActiveSession(payload.userId());
        // 活跃会话存在但不是这一枚 → 已被新登录顶下线的旧令牌，不能续
        if (activeTokenId != null && !Objects.equals(activeTokenId, payload.tokenId())) return null;

        User user = userRepository.findById(payload.userId()).orElse(null);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) return null;

        IssuedToken renewed = jwtService.renew(payload);
        authStore.bindActiveSession(payload.userId(), renewed.tokenId(), renewed.ttl());
        return renewed.token();
    }

    /**
     * 从 Authorization 请求头取值（形如 "Bearer xxx"）解析出 token 并返回对应用户。
     * null / 空白 / 无效 token 均返回 null。
     */
    public User getUserByBearerToken(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) return null;
        return getUserByToken(bearerToken.replace("Bearer ", ""));
    }

    /**
     * 解析 Bearer token 并强制登录态校验；未登录或 token 失效时抛出业务异常（401）。
     * 供各 Controller 复用。
     */
    public User requireLogin(String bearerToken) {
        return requireLogin(bearerToken, "未登录或登录已失效，请重新登录");
    }

    /**
     * 同 {@link #requireLogin(String)}，可自定义未登录提示文案
     */
    public User requireLogin(String bearerToken, String message) {
        User user = getUserByBearerToken(bearerToken);
        if (user == null) {
            throw new BizException(401, message);
        }
        return user;
    }

    public boolean validateToken(String token) {
        JwtPayload payload = jwtService.parse(token);
        return payload != null && !authStore.isBlacklisted(payload.tokenId());
    }

    /**
     * token 状态：VALID 有效 / KICKED 已被新登录顶下线 / INVALID 非法、已过期或已登出
     */
    public String getTokenStatus(String token) {
        JwtPayload payload = jwtService.parse(token);
        if (payload == null) return "INVALID";
        if (authStore.isBlacklisted(payload.tokenId())) return "INVALID";
        String activeTokenId = authStore.getActiveSession(payload.userId());
        if (activeTokenId == null || Objects.equals(activeTokenId, payload.tokenId())) return "VALID";
        return "KICKED";
    }

    /** 令牌剩余有效期（已过期返回负值） */
    private Duration remainingTtl(JwtPayload payload) {
        if (payload.expiresAt() == null) return Duration.ZERO;
        return Duration.between(Instant.now(), payload.expiresAt());
    }

    /** 登录失败次数达到上限后临时锁定（按账号维度，跨实例生效） */
    private void assertLoginNotLocked(String username) {
        if (authStore.count(LOGIN_FAIL_KEY + username) >= loginMaxFailures) {
            throw new BizException(429, "登录失败次数过多，账号已临时锁定，请 " + loginLockMinutes + " 分钟后再试");
        }
    }

    private void recordLoginFailure(String username) {
        long failures = authStore.increment(LOGIN_FAIL_KEY + username, Duration.ofMinutes(loginLockMinutes));
        if (failures >= loginMaxFailures) {
            throw new BizException(429, "登录失败次数过多，账号已临时锁定，请 " + loginLockMinutes + " 分钟后再试");
        }
    }

    /**
     * 校验密码：BCrypt 哈希校验；历史明文密码按明文比对，通过后自动升级为 BCrypt
     */
    private boolean verifyPassword(String rawPassword, User user) {
        String stored = user.getPassword();
        if (PasswordUtil.isEncoded(stored)) {
            return PasswordUtil.matches(rawPassword, stored);
        }
        // 历史明文数据：兼容校验，成功后原地升级为加密存储
        boolean ok = stored != null && stored.equals(rawPassword);
        if (ok) {
            user.setPassword(PasswordUtil.encode(rawPassword));
            user.setUpdateTime(LocalDateTime.now());
            userRepository.save(user);
        }
        return ok;
    }

    /**
     * 学生注册：仅需学号、密码、验证码
     * @param request  注册请求
     * @param clientIp 客户端来源 IP（用于同一设备短时间限注册）
     */
    public LoginResponse register(RegisterRequest request, String clientIp) {
        // 1. 校验图形验证码
        if (!captchaService.validate(request.getCaptchaId(), request.getCaptchaCode())) {
            throw new BizException(400, "验证码错误或已过期，请刷新后重试");
        }

        // 2. 校验必填字段
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            throw new BizException(400, "学号不能为空");
        }
        if (request.getPassword() == null || request.getPassword().length() < 6) {
            throw new BizException(400, "密码至少6位");
        }

        // 3. 同一设备（按来源IP）在窗口期内不可重复注册
        String ipKey = REGISTER_KEY + ((clientIp == null || clientIp.isEmpty()) ? "unknown" : clientIp);
        if (authStore.count(ipKey) > 0) {
            throw new BizException(429, "该设备刚刚完成过注册，请稍后再试");
        }

        // 4. 检查学号是否已存在
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new BizException(400, "该学号已注册，请直接登录");
        }

        // 5. 创建用户：注册仅需学号/密码/验证码，其余信息留空，显示名默认取学号；密码加密存储
        User user = new User();
        user.setUsername(request.getUsername().trim());
        user.setPassword(PasswordUtil.encode(request.getPassword()));
        user.setRealName(request.getRealName() != null && !request.getRealName().trim().isEmpty()
                ? request.getRealName().trim() : request.getUsername().trim());
        user.setRole("STUDENT");
        user.setCollege(request.getCollege());
        user.setMajor(request.getMajor());
        user.setGrade(request.getGrade());
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userRepository.save(user);
        authStore.increment(ipKey, Duration.ofSeconds(Math.max(1, registerCooldownSeconds)));

        // 6. 注册成功后自动登录（登记为唯一活跃会话）
        return createSession(user);
    }

    /**
     * 修改个人资料（学号、角色、密码不可在此修改）
     */
    public User updateProfile(Long userId, User profile) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(400, "用户不存在"));
        if (profile.getRealName() != null && !profile.getRealName().trim().isEmpty()) {
            user.setRealName(profile.getRealName().trim());
        }
        if (profile.getCollege() != null) user.setCollege(profile.getCollege().trim());
        if (profile.getMajor() != null) user.setMajor(profile.getMajor().trim());
        if (profile.getGrade() != null) user.setGrade(profile.getGrade().trim());
        if (profile.getPhone() != null) user.setPhone(profile.getPhone().trim());
        if (profile.getEmail() != null) user.setEmail(profile.getEmail().trim());
        user.setUpdateTime(LocalDateTime.now());
        return userRepository.save(user);
    }

    /**
     * 修改密码：校验原密码后，新密码加密存储
     */
    public void updatePassword(Long userId, String oldPassword, String newPassword) {
        if (oldPassword == null || oldPassword.isEmpty()) {
            throw new BizException(400, "请输入原密码");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new BizException(400, "新密码至少6位");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(400, "用户不存在"));
        if (!verifyPassword(oldPassword, user)) {
            throw new BizException(400, "原密码错误");
        }
        user.setPassword(PasswordUtil.encode(newPassword));
        user.setUpdateTime(LocalDateTime.now());
        userRepository.save(user);
    }
}
