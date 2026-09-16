package com.gxu.aihall.service;

import com.gxu.aihall.common.BizException;
import com.gxu.aihall.entity.UserBinding;
import com.gxu.aihall.repository.UserBindingRepository;
import com.gxu.aihall.util.AesUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.math.BigInteger;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 校园平台绑定服务：
 * 教务系统（jwxt2018.gxu.edu.cn）—— 无验证码，后端全自动模拟登录
 * 绑定成功仅保存目标平台会话 Cookie 与账号，不保存密码
 */
@Service
public class PlatformBindService {

    private static final String JWXT_LOGIN_URL = "https://jwxt2018.gxu.edu.cn/jwglxt/xtgl/login_slogin.html";
    private static final String JWXT_PUBKEY_URL = "https://jwxt2018.gxu.edu.cn/jwglxt/xtgl/login_getPublicKey.html";

    private final UserBindingRepository bindingRepository;

    @Value("${app.jwt.secret}")
    private String encryptSecret;

    public PlatformBindService(UserBindingRepository bindingRepository) {
        this.bindingRepository = bindingRepository;
    }

    // ==================== 内部结构 ====================

    /** 一次绑定流程使用的 HTTP 客户端（自带独立 Cookie 会话） */
    public static class BindClient {
        public final HttpClient http;
        public final CookieManager cookies;
        public String username;

        BindClient() throws Exception {
            cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
            TrustManager[] trustAll = new TrustManager[]{
                    new X509TrustManager() {
                        public void checkClientTrusted(X509Certificate[] c, String a) {}
                        public void checkServerTrusted(X509Certificate[] c, String a) {}
                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    }
            };
            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(null, trustAll, new SecureRandom());
            http = HttpClient.newBuilder()
                    .sslContext(ssl)
                    .cookieHandler(cookies)
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
        }
    }

    /** 绑定结果 */
    public static class BindResult {
        public boolean success;
        public String message;
        public String account;
    }

    // ==================== 教务系统绑定（全自动） ====================

    public BindResult jwxtBind(Long userId, String username, String password) throws Exception {
        if (username == null || username.trim().isEmpty()) throw new RuntimeException("请输入学号");
        if (password == null || password.isEmpty()) throw new RuntimeException("请输入密码");

        BindClient bc = new BindClient();
        // 1. 访问登录页，取 csrftoken 与 Cookie
        String loginHtml = get(bc, JWXT_LOGIN_URL);
        String csrf = extract(loginHtml, "id=\"csrftoken\"\\s+name=\"csrftoken\"\\s+value=\"([^\"]+)\"");
        if (csrf == null) {
            throw new RuntimeException("暂时连不上教务系统，请稍后再试");
        }
        // 2. 获取 RSA 公钥
        String pubJson = get(bc, JWXT_PUBKEY_URL);
        String modulus = extract(pubJson, "\"modulus\"\\s*:\\s*\"([^\"]+)\"");
        String exponent = extract(pubJson, "\"exponent\"\\s*:\\s*\"([^\"]+)\"");
        if (modulus == null || exponent == null) {
            throw new RuntimeException("暂时连不上教务系统，请稍后再试");
        }
        // JSON 中 / 会被转义为 \/，需反转义后再做 base64 解码
        modulus = unescapeJson(modulus);
        exponent = unescapeJson(exponent);
        // 3. 密码 RSA 加密后提交登录
        String mm = rsaEncrypt(password, modulus, exponent);
        String body = "csrftoken=" + urlEncode(csrf)
                + "&yhm=" + urlEncode(username)
                + "&mm=" + urlEncode(mm);
        PostResp resp = post(bc, JWXT_LOGIN_URL, body);

        if (isRedirect(resp.status)) {
            saveBinding(userId, "JWXT", username, dumpCookies(bc), AesUtil.encrypt(password, encryptSecret));
            BindResult r = new BindResult();
            r.success = true;
            r.message = "绑定成功";
            r.account = username;
            return r;
        }
        // 失败：登录页可能要求输入验证码
        String failHtml = resp.body == null ? "" : resp.body;
        if (failHtml.contains("id=\"yzm\"") || failHtml.contains("yzmDiv")) {
            throw new RuntimeException("教务系统要求输入验证码，请稍后再试");
        }
        throw new RuntimeException("账号或密码错误，绑定失败");
    }

    // ==================== 绑定状态 ====================

    public List<Map<String, Object>> bindStatus(Long userId) {
        List<UserBinding> list = bindingRepository.findByUserId(userId);
        Map<String, UserBinding> byPlatform = new HashMap<>();
        for (UserBinding b : list) {
            if (b.getStatus() != null && b.getStatus() == 1) byPlatform.put(b.getPlatform(), b);
        }
        return List.of(bindStatusItem(byPlatform.get("JWXT"), "JWXT"));
    }

    private Map<String, Object> bindStatusItem(UserBinding b, String platform) {
        Map<String, Object> item = new HashMap<>();
        item.put("platform", platform);
        if (b != null && b.getBindAccount() != null) {
            item.put("bound", true);
            item.put("account", b.getBindAccount());
        } else {
            item.put("bound", false);
            item.put("account", "");
        }
        return item;
    }

    // ==================== 存储 ====================

    private void saveBinding(Long userId, String platform, String account, String cookie, String encryptedPassword) {
        UserBinding binding = bindingRepository.findByUserIdAndPlatform(userId, platform).orElse(null);

        // 一个教务账号只能被一个平台账号活跃绑定。
        // 否则「学校账号登录」用 findFirst 反查时可能把 B 登进 A 的账号。
        // 这里一律校验（含同账号刷新 Cookie 的场景）：清理完历史重复数据后（见
        // backups/user_binding_backup_*.sql / backend/db/indexes.sql），正常刷新不会命中冲突，
        // 而重复占用会得到明确提示，而不是抛出数据库唯一键异常。
        // 数据库侧还有 uk_user_binding_active 兜底（生成列 + 唯一索引）。
        for (UserBinding other : bindingRepository.findByPlatformAndBindAccount(platform, account)) {
            if (!java.util.Objects.equals(other.getUserId(), userId)
                    && other.getStatus() != null && other.getStatus() == 1) {
                throw new BizException("该学校账号已被其他平台账号绑定，请先联系管理员解绑，或改用其他账号绑定");
            }
        }

        if (binding == null) {
            binding = new UserBinding();
            binding.setUserId(userId);
            binding.setPlatform(platform);
            binding.setCreateTime(LocalDateTime.now());
        }
        binding.setBindAccount(account);
        binding.setCookie(cookie);
        if (encryptedPassword != null) {
            binding.setEncryptedPassword(encryptedPassword);
        }
        binding.setStatus(1);
        binding.setUpdateTime(LocalDateTime.now());
        bindingRepository.save(binding);
    }

    // ==================== 用已保存的加密密码重新登录 ====================

    /**
     * 用数据库中保存的加密密码重新登录教务系统，返回带会话的 BindClient。
     * cookie 过期后调用此方法可重新获取有效会话。
     */
    public BindClient jwxtRelogin(Long userId) throws Exception {
        UserBinding binding = bindingRepository.findByUserIdAndPlatform(userId, "JWXT").orElse(null);
        if (binding == null || binding.getEncryptedPassword() == null) {
            throw new RuntimeException("尚未绑定教务系统，或绑定时间较早未保存密码，请重新绑定");
        }
        String password = AesUtil.decrypt(binding.getEncryptedPassword(), encryptSecret);
        String username = binding.getBindAccount();

        BindClient bc = new BindClient();
        bc.username = username;
        // 1. 访问登录页，取 csrftoken 与 Cookie
        String loginHtml = get(bc, JWXT_LOGIN_URL);
        String csrf = extract(loginHtml, "id=\"csrftoken\"\\s+name=\"csrftoken\"\\s+value=\"([^\"]+)\"");
        if (csrf == null) {
            throw new RuntimeException("暂时连不上教务系统，请稍后再试");
        }
        // 2. 获取 RSA 公钥
        String pubJson = get(bc, JWXT_PUBKEY_URL);
        String modulus = extract(pubJson, "\"modulus\"\\s*:\\s*\"([^\"]+)\"");
        String exponent = extract(pubJson, "\"exponent\"\\s*:\\s*\"([^\"]+)\"");
        if (modulus == null || exponent == null) {
            throw new RuntimeException("暂时连不上教务系统，请稍后再试");
        }
        modulus = unescapeJson(modulus);
        exponent = unescapeJson(exponent);
        // 3. 密码 RSA 加密后提交登录
        String mm = rsaEncrypt(password, modulus, exponent);
        String body = "csrftoken=" + urlEncode(csrf)
                + "&yhm=" + urlEncode(username)
                + "&mm=" + urlEncode(mm);
        PostResp resp = post(bc, JWXT_LOGIN_URL, body);

        if (!isRedirect(resp.status)) {
            String failHtml = resp.body == null ? "" : resp.body;
            if (failHtml.contains("id=\"yzm\"") || failHtml.contains("yzmDiv")) {
                throw new RuntimeException("教务系统要求输入验证码，请稍后再试");
            }
            throw new RuntimeException("教务系统账号或密码错误，请重新绑定");
        }
        // 登录成功，更新 cookie（不更新密码）
        saveBinding(userId, "JWXT", username, dumpCookies(bc), null);
        return bc;
    }

    /** 用 BindClient 发送 GET 请求（公开，供其他 Service 调用） */
    public String getWithClient(BindClient bc, String url) throws Exception {
        return get(bc, url);
    }

    /** 用 BindClient 发送 POST 表单请求（公开，供其他 Service 调用） */
    public String postFormWithClient(BindClient bc, String url, String formBody) throws Exception {
        return postFormWithClient(bc, url, formBody, null);
    }

    /** 用 BindClient 发送 POST 表单请求，可指定 Referer */
    public String postFormWithClient(BindClient bc, String url, String formBody, String referer) throws Exception {
        PostResp resp = post(bc, url, formBody, referer);
        return resp.body;
    }

    // ==================== 工具方法 ====================

    private String get(BindClient bc, String url) throws Exception {
        return decode(getBytes(bc, url));
    }

    private byte[] getBytes(BindClient bc, String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36")
                .GET().build();
        HttpResponse<byte[]> resp = bc.http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        return resp.body();
    }

    private PostResp post(BindClient bc, String url, String formBody) throws Exception {
        return post(bc, url, formBody, null);
    }

    private PostResp post(BindClient bc, String url, String formBody, String referer) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36")
                .header("Content-Type", "application/x-www-form-urlencoded");
        if (referer != null && !referer.isEmpty()) {
            builder.header("Referer", referer);
        }
        HttpRequest req = builder.POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8)).build();
        HttpResponse<byte[]> resp = bc.http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        String location = resp.headers().firstValue("Location").orElse(null);
        return new PostResp(resp.statusCode(), decode(resp.body()), location);
    }

    /** 简易 POST 响应（状态码 + 已解码正文 + 重定向地址） */
    private static class PostResp {
        final int status;
        final String body;
        final String location;

        PostResp(int status, String body, String location) {
            this.status = status;
            this.body = body;
            this.location = location;
        }
    }

    /** 目标平台页面可能是 UTF-8 也可能是 GBK：优先按 UTF-8 解码，出现乱码时回退 GBK */
    private String decode(byte[] bytes) {
        if (bytes == null) return "";
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        if (!utf8.contains("\uFFFD")) return utf8;
        try {
            return new String(bytes, java.nio.charset.Charset.forName("GBK"));
        } catch (Exception e) {
            return utf8;
        }
    }

    private boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private String extract(String text, String regex) {
        if (text == null) return null;
        Matcher m = Pattern.compile(regex).matcher(text);
        return m.find() ? m.group(1) : null;
    }

    /** JSON 字符串里的 \/ 反转义为 /（公钥 base64 中可能出现） */
    private String unescapeJson(String s) {
        return s == null ? null : s.replace("\\/", "/");
    }

    private String urlEncode(String s) throws Exception {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8.name());
    }

    private String rsaEncrypt(String plain, String modulus, String exponent) throws Exception {
        byte[] mBytes = decodeKeyPart(modulus);
        byte[] eBytes = decodeKeyPart(exponent);
        RSAPublicKeySpec spec = new RSAPublicKeySpec(new BigInteger(1, mBytes), new BigInteger(1, eBytes));
        PublicKey pub = KeyFactory.getInstance("RSA").generatePublic(spec);
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, pub);
        return Base64.getEncoder().encodeToString(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
    }

    /** 自动识别并解码公钥字段：纯十六进制按 hex 解码（长度奇时高位补 0），否则按 base64 */
    private byte[] decodeKeyPart(String s) {
        String t = s == null ? "" : s.trim();
        if (!t.isEmpty() && t.matches("[0-9a-fA-F]+")) {
            if (t.length() % 2 != 0) t = "0" + t;
            byte[] out = new byte[t.length() / 2];
            for (int i = 0; i < out.length; i++) {
                out[i] = (byte) Integer.parseInt(t.substring(i * 2, i * 2 + 2), 16);
            }
            return out;
        }
        return Base64.getDecoder().decode(t);
    }

    /** 导出本次会话保存的所有 Cookie（仅名称=值，不含敏感属性） */
    private String dumpCookies(BindClient bc) {
        List<String> parts = new ArrayList<>();
        for (HttpCookie c : bc.cookies.getCookieStore().getCookies()) {
            parts.add(c.getName() + "=" + c.getValue());
        }
        return String.join("; ", parts);
    }
}
