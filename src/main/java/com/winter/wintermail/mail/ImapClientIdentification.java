package com.winter.wintermail.mail;

import com.sun.mail.imap.IMAPStore;
import javax.mail.MessagingException;
import java.util.Map;

/** 网易 IMAP 服务器要求客户端 ID；其他厂商无需发送该扩展命令。 */
public final class ImapClientIdentification {
    private ImapClientIdentification() {}
    /** 仅网易 163/126 在登录后发送 IMAP ID，兼容厂商的客户端识别要求。 */
    public static void identify(String provider, IMAPStore store) throws MessagingException {
        if ("163".equals(provider) || "126".equals(provider)) {
            store.id(Map.of("name", "Winter Mail", "version", "1.0", "vendor", "Winter Mail"));
        }
    }
}
