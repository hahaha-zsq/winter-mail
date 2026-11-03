package com.winter.wintermail.mail;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * MySQL 邮箱账号表的持久化模型；credential 只存加密后的授权码。
 */
@TableName("mail_accounts")
public class AccountEntity {
    @TableId(type = IdType.INPUT)
    private String id;
    /**
     * 完整邮箱地址，用于 IMAP/SMTP 身份验证和前端显示。
     */
    private String email;
    /**
     * AES-GCM 密文，不能把原始授权码写入数据库。
     */
    private String credential;
    /**
     * 厂商标识，例如 gmail、qq、163、126 或 custom。
     */
    private String provider;
    /**
     * 收件服务器地址和端口；与 SMTP 参数分别存储，便于自定义厂商。
     */
    private String imapHost;
    private Integer imapPort;
    /**
     * 发件服务器地址和端口。
     */
    private String smtpHost;
    private Integer smtpPort;
    /**
     * true 表示 SMTP 隐式 SSL；false 表示连接后升级为 STARTTLS。
     */
    private Boolean smtpSsl;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCredential() {
        return credential;
    }

    public void setCredential(String credential) {
        this.credential = credential;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getImapHost() {
        return imapHost;
    }

    public void setImapHost(String imapHost) {
        this.imapHost = imapHost;
    }

    public Integer getImapPort() {
        return imapPort;
    }

    public void setImapPort(Integer imapPort) {
        this.imapPort = imapPort;
    }

    public String getSmtpHost() {
        return smtpHost;
    }

    public void setSmtpHost(String smtpHost) {
        this.smtpHost = smtpHost;
    }

    public Integer getSmtpPort() {
        return smtpPort;
    }

    public void setSmtpPort(Integer smtpPort) {
        this.smtpPort = smtpPort;
    }

    public Boolean getSmtpSsl() {
        return smtpSsl;
    }

    public void setSmtpSsl(Boolean smtpSsl) {
        this.smtpSsl = smtpSsl;
    }
}
