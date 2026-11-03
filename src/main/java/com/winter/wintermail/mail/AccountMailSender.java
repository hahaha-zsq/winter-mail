package com.winter.wintermail.mail;

import com.winter.wintermail.mail.AccountRepository.Account;
import com.zsq.winter.mail.entity.MailDTO;
import com.zsq.winter.mail.service.WinterMailServiceImpl;
import com.zsq.winter.mail.service.WinterMailTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Properties;
import java.util.function.Function;

/** 为每个账号创建独立 SMTP 客户端，并通过自定义 starter 发送邮件。 */
@Service
public class AccountMailSender {
    private final Function<Account, JavaMailSender> senderFactory;

    public AccountMailSender() { this(AccountMailSender::createSender); }
    AccountMailSender(Function<Account, JavaMailSender> senderFactory) { this.senderFactory = senderFactory; }

    /** 纯文本附件使用 MimeMessageHelper；其余格式委托 WinterMailTemplate。 */
    public boolean send(Account account, String recipient, List<String> cc, String subject, String body, String format, MultipartFile[] attachments) throws Exception {
        String normalized = format == null || format.isBlank() ? "text" : format.toLowerCase(java.util.Locale.ROOT);
        if (!normalized.equals("text") && !normalized.equals("html")) throw new IllegalArgumentException("邮件格式仅支持 text 或 html");
        if (body == null || body.isBlank()) throw new IllegalArgumentException("请填写邮件正文");
        // starter 接受 MailDTO；一封邮件只设置一个 To，批量循环在 API 层执行。
        MailDTO message = new MailDTO();
        message.setFrom(account.email());
        message.setTo(List.of(recipient));
        message.setCc(cc);
        message.setSubject(subject);
        message.setText(body);
        MultipartFile[] files = attachments == null ? new MultipartFile[0] : attachments;
        JavaMailSender sender = senderFactory.apply(account);
        // 自定义 starter 的纯文本附件路径不匹配需求，此处分支显式生成 MIME 邮件。
        if (normalized.equals("text") && files.length > 0) {
            MimeMessageHelper mime = new MimeMessageHelper(sender.createMimeMessage(), true, "UTF-8");
            mime.setFrom(account.email());
            mime.setTo(recipient);
            if (!cc.isEmpty()) mime.setCc(cc.toArray(String[]::new));
            mime.setSubject(subject);
            mime.setText(body, false);
            for (MultipartFile file : files) mime.addAttachment(file.getOriginalFilename(), file);
            sender.send(mime.getMimeMessage());
            return true;
        }
        message.setAttachmentArr(files);
        // 普通文本与 HTML 模板邮件仍通过项目指定的 WinterMailTemplate 发送。
        WinterMailTemplate template = new WinterMailTemplate(new WinterMailServiceImpl(sender));
        return normalized.equals("html") ? template.sendTemplateEmail(message) : template.sendMail(message);
    }

    /** 每个账号使用各自的主机、端口和授权码；SSL 与 STARTTLS 二选一。 */
    private static JavaMailSender createSender(Account account) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(account.settings().smtpHost());
        sender.setPort(account.settings().smtpPort());
        sender.setUsername(account.email());
        sender.setPassword(account.password());
        sender.setDefaultEncoding("UTF-8");
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        // 三种超时分别约束建连、响应读取和写入，避免请求无限挂起。
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        if (account.settings().smtpSsl()) props.put("mail.smtp.ssl.enable", "true");
        else { props.put("mail.smtp.starttls.enable", "true"); props.put("mail.smtp.starttls.required", "true"); }
        return sender;
    }
}
