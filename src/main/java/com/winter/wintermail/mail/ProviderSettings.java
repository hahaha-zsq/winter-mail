package com.winter.wintermail.mail;

/** 单个邮箱厂商的 IMAP 收件与 SMTP 发件参数。 */
public record ProviderSettings(String imapHost, int imapPort, String smtpHost, int smtpPort, boolean smtpSsl) {
    /** 便捷入口：由策略注册表生成厂商默认参数，不接受自定义主机。 */
    public static ProviderSettings forProvider(String provider) {
        return new ProviderStrategyRegistry().resolve(provider, null, null, null, null, null);
    }
}
