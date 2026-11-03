package com.winter.wintermail.mail;

import java.util.Map;
import java.util.function.Supplier;

/** 按厂商选择固定服务器配置，或采用用户填写的自定义配置。 */
public class ProviderStrategyRegistry {
    /** 每种厂商把用户输入转换为最终的收发服务器配置。 */
    private interface ProviderStrategy {
        ProviderSettings settings(String imapHost, Integer imapPort, String smtpHost, Integer smtpPort, Boolean smtpSsl);
    }

    /** 新增厂商时在此登记策略，避免把厂商判断分散到 API 和发信代码。 */
    private final Map<String, ProviderStrategy> strategies = Map.of(
            "gmail", fixed(() -> new ProviderSettings("imap.gmail.com", 993, "smtp.gmail.com", 587, false)),
            "qq", fixed(() -> new ProviderSettings("imap.qq.com", 993, "smtp.qq.com", 465, true)),
            "163", fixed(() -> new ProviderSettings("imap.163.com", 993, "smtp.163.com", 465, true)),
            "126", fixed(() -> new ProviderSettings("imap.126.com", 993, "smtp.126.com", 465, true)),
            // 自定义厂商保留用户填写的主机，端口缺失时使用常见默认值。
            "custom", (imapHost, imapPort, smtpHost, smtpPort, smtpSsl) -> new ProviderSettings(imapHost, imapPort == null ? 993 : imapPort, smtpHost, smtpPort == null ? 465 : smtpPort, Boolean.TRUE.equals(smtpSsl))
    );

    /** 固定厂商忽略表单里可能携带的主机与端口，使用已知配置。 */
    private ProviderStrategy fixed(Supplier<ProviderSettings> supplier) {
        return (a, b, c, d, e) -> supplier.get();
    }

    /** 解析后统一校验主机和端口，防止无效配置进入数据库。 */
    public ProviderSettings resolve(String provider, String imapHost, Integer imapPort, String smtpHost, Integer smtpPort, Boolean smtpSsl) {
        ProviderStrategy strategy = strategies.get(provider == null ? "custom" : provider.toLowerCase());
        if (strategy == null) throw new IllegalArgumentException("不支持该邮箱厂商，请使用自定义配置");
        ProviderSettings result = strategy.settings(imapHost, imapPort, smtpHost, smtpPort, smtpSsl);
        if (result.imapHost() == null || result.imapHost().isBlank() || result.smtpHost() == null || result.smtpHost().isBlank() || result.imapPort() < 1 || result.imapPort() > 65535 || result.smtpPort() < 1 || result.smtpPort() > 65535)
            throw new IllegalArgumentException("请填写有效的服务器地址和端口");
        return result;
    }
}
