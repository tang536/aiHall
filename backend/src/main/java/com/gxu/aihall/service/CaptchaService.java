package com.gxu.aihall.service;

import com.gxu.aihall.security.AuthStore;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * 图形验证码服务
 * 生成 4 位随机字符验证码图片（base64），用 captchaId 暂存，5 分钟有效，一次性使用。
 * 暂存走 {@link AuthStore}：内存模式下为进程内 Map，Redis 模式下多实例共享，
 * 因此前端在 A 实例取图、请求落到 B 实例也能校验通过。
 */
@Service
public class CaptchaService {

    // 排除易混淆字符 0/O/1/I/L
    private static final char[] CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int WIDTH = 120;
    private static final int HEIGHT = 40;
    private static final int CODE_LEN = 4;
    private static final Duration EXPIRE = Duration.ofMinutes(5);

    private static final String CAPTCHA_KEY = "captcha:";

    private final AuthStore authStore;
    private final Random random = new Random();

    public CaptchaService(AuthStore authStore) {
        this.authStore = authStore;
    }

    /**
     * 生成验证码，返回 captchaId 和 base64 图片
     */
    public Map<String, String> generate() {
        String code = randomCode();
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        authStore.put(CAPTCHA_KEY + captchaId, code, EXPIRE);

        String imageBase64 = drawImage(code);
        return Map.of("captchaId", captchaId, "image", imageBase64);
    }

    /**
     * 校验验证码（一次性，校验成功后立即删除）
     * @return true 校验通过
     */
    public boolean validate(String captchaId, String inputCode) {
        if (captchaId == null || inputCode == null) return false;
        // 读取即删除：无论校验是否通过都作废，避免同一验证码被反复试错
        String code = authStore.take(CAPTCHA_KEY + captchaId);
        return code != null && code.equalsIgnoreCase(inputCode.trim());
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LEN);
        for (int i = 0; i < CODE_LEN; i++) {
            sb.append(CHARS[random.nextInt(CHARS.length)]);
        }
        return sb.toString();
    }

    private String drawImage(String code) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 背景
        g.setColor(new Color(245, 247, 250));
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // 干扰线
        for (int i = 0; i < 4; i++) {
            g.setColor(randomColor(150, 220));
            g.drawLine(random.nextInt(WIDTH), random.nextInt(HEIGHT),
                    random.nextInt(WIDTH), random.nextInt(HEIGHT));
        }

        // 干扰点
        for (int i = 0; i < 30; i++) {
            g.setColor(randomColor(180, 230));
            g.fillOval(random.nextInt(WIDTH), random.nextInt(HEIGHT), 2, 2);
        }

        // 字符
        g.setFont(new Font("Arial", Font.BOLD, 26));
        for (int i = 0; i < code.length(); i++) {
            g.setColor(randomColor(30, 130));
            // 轻微旋转
            double theta = (random.nextDouble() - 0.5) * 0.4;
            int x = 18 + i * 24;
            int y = 28 + random.nextInt(6) - 3;
            g.rotate(theta, x, y);
            g.drawString(String.valueOf(code.charAt(i)), x, y);
            g.rotate(-theta, x, y);
        }
        g.dispose();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("验证码图片生成失败", e);
        }
    }

    private Color randomColor(int min, int max) {
        int r = min + random.nextInt(max - min);
        int g = min + random.nextInt(max - min);
        int b = min + random.nextInt(max - min);
        return new Color(r, g, b);
    }
}
