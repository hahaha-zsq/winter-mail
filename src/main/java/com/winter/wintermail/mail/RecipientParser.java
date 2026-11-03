package com.winter.wintermail.mail;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import java.util.*;

/**
 * 解析批量收件人：支持常见分隔符，保序去重并限制单次发送规模。
 */
public class RecipientParser {
    /**
     * 逗号、分号、空白均可分隔地址；返回小写且保持首次出现顺序。
     */
    public static List<String> parse(String input) {
        // LinkedHashSet 同时完成去重和保序，避免同一收件人收到重复邮件。
        Set<String> addresses = new LinkedHashSet<>();
        if (input != null) for (String part : input.split("[,;\\s]+")) {
            if (part.isBlank()) continue;
            try {
                InternetAddress address = new InternetAddress(part, true);
                // JavaMail 校验语法；额外检查 @ 是为了拒绝不完整的输入。
                address.validate();
                if (!part.contains("@") || part.startsWith("@") || part.endsWith("@")) throw new AddressException();
                addresses.add(part.toLowerCase(Locale.ROOT));
            } catch (AddressException e) {
                throw new IllegalArgumentException("无效收件人: " + part);
            }
        }
        if (addresses.isEmpty()) throw new IllegalArgumentException("请填写收件人");
        if (addresses.size() > 100) throw new IllegalArgumentException("单次最多发送 100 位收件人");
        return new ArrayList<>(addresses);
    }
}
