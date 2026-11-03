package com.winter.wintermail.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/** 使用 256 位 AES-GCM 保护邮箱授权码；密钥由环境配置提供。 */
@Component
public class CredentialCipher {
    private final SecretKeySpec key;

    /** Base64 解码后必须恰好 32 字节，对应 AES-256 密钥。 */
    public CredentialCipher(@Value("${mail.credential-key}") String encodedKey) {
        byte[] bytes = Base64.getDecoder().decode(encodedKey);
        if (bytes.length != 32) throw new IllegalArgumentException("MAIL_CREDENTIAL_KEY 必须是 32 字节的 Base64 密钥");
        key = new SecretKeySpec(bytes, "AES");
    }

    /** 每次加密生成独立的 12 字节随机 nonce，并把 nonce 放在密文前。 */
    public String encrypt(String plain) {
        try {
            byte[] nonce = new byte[12];
            new SecureRandom().nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            byte[] ciphertext = cipher.doFinal(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            // 存储格式为 nonce(12 字节) + 密文及 GCM 标签，整体再次做 Base64。
            byte[] result = Arrays.copyOf(nonce, nonce.length + ciphertext.length);
            System.arraycopy(ciphertext, 0, result, nonce.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            throw new IllegalStateException("加密邮箱授权码失败", e);
        }
    }

    /** 从密文前缀恢复 nonce；GCM 标签校验失败时拒绝返回明文。 */
    public String decrypt(String encoded) {
        try {
            byte[] data = Base64.getDecoder().decode(encoded);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Arrays.copyOf(data, 12)));
            // doFinal 同时校验 GCM 认证标签；密钥错误或密文被改动会抛异常。
            return new String(cipher.doFinal(data, 12, data.length - 12), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("解密邮箱授权码失败，请检查加密密钥", e);
        }
    }
}
