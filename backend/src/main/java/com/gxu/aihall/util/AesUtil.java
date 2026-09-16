package com.gxu.aihall.util;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * AES 加密工具（CBC 模式，用于加密存储校园平台密码）
 */
public class AesUtil {

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final int KEY_LEN = 16; // AES-128
    private static final int IV_LEN = 16;

    /** 从配置密钥派生固定的 16 字节 key 和 iv（iv 也从密钥派生，简化实现） */
    private static SecretKeySpec deriveKey(String secret) {
        byte[] keyBytes = new byte[KEY_LEN];
        byte[] src = secret.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(src, 0, keyBytes, 0, Math.min(src.length, KEY_LEN));
        return new SecretKeySpec(keyBytes, "AES");
    }

    private static IvParameterSpec deriveIv(String secret) {
        byte[] ivBytes = new byte[IV_LEN];
        byte[] src = secret.getBytes(StandardCharsets.UTF_8);
        // 用密钥的后 16 字节（不足则循环）作为 IV
        for (int i = 0; i < IV_LEN; i++) {
            ivBytes[i] = src[(i + KEY_LEN) % src.length];
        }
        return new IvParameterSpec(ivBytes);
    }

    public static String encrypt(String plainText, String secret) {
        if (plainText == null) return null;
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(secret), deriveIv(secret));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("AES 加密失败", e);
        }
    }

    public static String decrypt(String cipherText, String secret) {
        if (cipherText == null) return null;
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(secret), deriveIv(secret));
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(cipherText));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES 解密失败", e);
        }
    }
}
