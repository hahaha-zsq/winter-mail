package com.winter.wintermail.mail;

import org.jsoup.Jsoup;

import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.MimeUtility;
import java.util.ArrayList;
import java.util.List;

/**
 * 递归读取 MIME 结构，提取正文、HTML 摘要和附件元数据。
 */
final class MailContentExtractor {
    /**
     * 附件序号与详情接口的遍历顺序一致，size 为未知时可能是 -1。
     */
    record Attachment(int index, String name, String contentType, int size) {
    }

    /**
     * 完整正文、用于列表的短摘要和附件元数据一次提取。
     */
    record Content(String text, String html, String preview, List<Attachment> attachments) {
    }

    private static final class Collected {
        String text = "";
        String html = "";
        final List<Part> parts = new ArrayList<>();
        final List<Attachment> attachments = new ArrayList<>();
    }

    /**
     * 优先用纯文本生成摘要；只有 HTML 时通过 HTML 解析器提取可读文字。
     */
    static Content read(Part root) throws Exception {
        Collected result = collect(root);
        // HTML-only 邮件没有纯文本部分时，先去标签并解码实体再生成摘要。
        String source = result.text.isBlank() ? Jsoup.parse(result.html).text() : result.text;
        String normalized = source.replaceAll("\\s+", " ").trim();
        return new Content(result.text, result.html, normalized.substring(0, Math.min(180, normalized.length())), List.copyOf(result.attachments));
    }

    /**
     * 与详情接口使用相同的遍历顺序定位附件，避免下载错位。
     */
    static Part attachment(Part root, int index) throws Exception {
        Collected result = collect(root);
        if (index < 0 || index >= result.parts.size()) throw new IllegalArgumentException("附件不存在");
        return result.parts.get(index);
    }

    private static Collected collect(Part root) throws Exception {
        Collected result = new Collected();
        visit(root, result);
        return result;
    }

    /**
     * 带文件名或 attachment 标记的 MIME 部分不参与正文摘要。
     */
    private static void visit(Part part, Collected result) throws Exception {
        String filename = part.getFileName();
        // 部分服务商不设置 disposition，因此有文件名也按附件处理。
        if (filename != null || Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
            int index = result.parts.size();
            String name = filename == null || filename.isBlank() ? "附件 " + (index + 1) : MimeUtility.decodeText(filename);
            result.parts.add(part);
            result.attachments.add(new Attachment(index, name, part.getContentType().split(";", 2)[0].trim(), part.getSize()));
            return;
        }
        // multipart/alternative 和 multipart/mixed 都可能嵌套，需递归遍历。
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) visit(multipart.getBodyPart(i), result);
        } else if (part.isMimeType("text/plain") && result.text.isEmpty()) {
            result.text = String.valueOf(part.getContent());
        } else if (part.isMimeType("text/html") && result.html.isEmpty()) {
            result.html = String.valueOf(part.getContent());
        }
    }

    private MailContentExtractor() {
    }
}
