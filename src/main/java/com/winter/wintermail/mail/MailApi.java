package com.winter.wintermail.mail;

import com.winter.wintermail.mail.AccountRepository.Account;
import com.sun.mail.imap.IMAPStore;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.*;

/**
 * 邮件 HTTP 接口。账号信息由 AccountRepository 保存，收件通过 IMAP，发件通过 AccountMailSender。
 * 列表只返回摘要；正文和附件在用户打开邮件时单独获取，减少首次加载的数据量。
 */
@RestController
@RequestMapping("/api")
public class MailApi {
    private static final Logger log = LoggerFactory.getLogger(MailApi.class);
    private final AccountRepository accounts;
    private final AccountMailSender sender;
    private final ProviderStrategyRegistry providers = new ProviderStrategyRegistry();

    public MailApi(AccountRepository accounts, AccountMailSender sender) {
        this.accounts = accounts;
        this.sender = sender;
    }

    /**
     * 新账号输入；密码字段是邮箱厂商的授权码，响应只使用 AccountView。
     */
    public record AccountInput(String email, String password, String provider, String imapHost, Integer imapPort,
                               String smtpHost, Integer smtpPort, Boolean smtpSsl) {
    }

    /**
     * 对外可见的账号字段，刻意不包含授权码和内部服务器配置。
     */
    public record AccountView(String id, String email, String provider) {
    }

    /**
     * 收件列表中的轻量数据；id 对应当前 IMAP 文件夹中的邮件序号。
     */
    public record MailView(String id, String from, String subject, String date, String preview) {
    }

    /**
     * 分页结果：hasMore 告诉前端是否继续请求下一批。
     */
    public record MailPage(List<MailView> messages, boolean hasMore) {
    }

    /**
     * 邮件详情同时保留纯文本与 HTML，由前端选择合适的展示方式。
     */
    public record MailDetail(String text, String html, List<MailContentExtractor.Attachment> attachments) {
    }

    /**
     * 发信输入：recipients 和 cc 可以包含多个地址，format 仅支持 text/html。
     */
    public record SendInput(String accountId, String recipients, String cc, String subject, String body,
                            String format) {
    }

    /**
     * 查询账号时只返回 AccountView，避免把解密后的授权码序列化到响应。
     */
    @GetMapping("/accounts")
    public List<AccountView> accounts() {
        return accounts.findAll().stream().map(a -> new AccountView(a.id(), a.email(), a.provider())).sorted(Comparator.comparing(AccountView::email)).toList();
    }

    /**
     * 校验输入、按厂商策略解析服务器参数，然后加密保存授权码。
     */
    @PostMapping("/accounts")
    public AccountView add(@RequestBody AccountInput input) {
        if (input.email() == null || !input.email().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || input.password() == null || input.password().isBlank())
            throw new IllegalArgumentException("请填写有效邮箱和授权码");
        String provider = input.provider() == null ? "custom" : input.provider();
        ProviderSettings settings = providers.resolve(provider, input.imapHost(), input.imapPort(), input.smtpHost(), input.smtpPort(), input.smtpSsl());
        if (settings.imapHost() == null || settings.imapHost().isBlank() || settings.smtpHost() == null || settings.smtpHost().isBlank() || settings.imapPort() < 1 || settings.imapPort() > 65535 || settings.smtpPort() < 1 || settings.smtpPort() > 65535)
            throw new IllegalArgumentException("请填写有效的服务器地址和端口");
        String id = UUID.randomUUID().toString();
        accounts.save(new Account(id, input.email(), input.password(), provider, settings));
        return new AccountView(id, input.email(), provider);
    }

    /**
     * 仅删除本地账号配置，不删除邮箱服务商服务器上的邮件。
     */
    @DeleteMapping("/accounts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable String id) {
        accounts.delete(id);
    }

    /**
     * 将不存在的账号统一转为客户端可理解的参数错误。
     */
    private Account account(String id) {
        Account a = accounts.find(id);
        if (a == null) throw new IllegalArgumentException("邮箱账号不存在");
        return a;
    }

    /**
     * 按 IMAP 序号从新到旧取信。默认 20 封，最多 30 封；offset 是已跳过的邮件数。
     * 账号间的全局排序和分页缓冲由前端处理，此接口一次只访问一个账号。
     */
    @GetMapping("/accounts/{id}/messages")
    public MailPage inbox(@PathVariable String id, @RequestParam(defaultValue = "20") int limit,
                          @RequestParam(defaultValue = "0") int offset) throws Exception {
        if (offset < 0 || limit < 1 || limit > 30) throw new IllegalArgumentException("分页参数无效，每页最多 30 封");
        Account a = account(id);
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", a.settings().imapHost());
        props.put("mail.imaps.port", String.valueOf(a.settings().imapPort()));
        props.put("mail.imaps.connectiontimeout", "10000");
        props.put("mail.imaps.timeout", "10000");
        // 网络超时同时限制建连和读信；只读打开 INBOX，不修改邮件已读状态。
        IMAPStore store = (IMAPStore) Session.getInstance(props).getStore("imaps");
        try {
            store.connect(a.settings().imapHost(), a.settings().imapPort(), a.email(), a.password());
            ImapClientIdentification.identify(a.provider(), store);
            Folder folder = store.getFolder("INBOX");
            folder.open(Folder.READ_ONLY);
            try {
                int total = folder.getMessageCount();
                List<MailView> result = new ArrayList<>();
                List<Integer> numbers = messageNumbers(total, offset, limit);
                for (int n : numbers) {
                    Message m = folder.getMessage(n);
                    Address[] from = m.getFrom();
                    result.add(new MailView(String.valueOf(n), formatFrom(from), m.getSubject(), m.getSentDate() == null ? "" : m.getSentDate().toInstant().toString(), MailContentExtractor.read(m).preview()));
                }
                return new MailPage(result, total > offset + numbers.size());
            } finally {
                // 无论读取哪封邮件失败，都先释放文件夹，再由外层 finally 断开连接。
                folder.close(false);
            }
        } finally {
            store.close();
        }
    }

    /**
     * 计算本页 IMAP 序号，确保超出末尾时返回空列表且不访问序号 0。
     */
    static List<Integer> messageNumbers(int total, int offset, int limit) {
        if (offset >= total) return List.of();
        int first = total - offset;
        List<Integer> numbers = new ArrayList<>();
        for (int number = first; number > Math.max(0, first - limit); number--) numbers.add(number);
        return numbers;
    }

    /**
     * getPersonal 会解码 MIME 编码的发件人名称；toString 会保留传输编码。
     */
    static String formatFrom(Address[] addresses) {
        if (addresses == null || addresses.length == 0) return "";
        Address sender = addresses[0];
        if (sender instanceof InternetAddress internet) {
            String name = internet.getPersonal();
            String email = internet.getAddress();
            return name == null || name.isBlank() ? email : name + " <" + email + ">";
        }
        return sender.toString();
    }

    /**
     * 点击邮件时再读取完整正文与附件元数据，列表请求仅返回摘要。
     */
    @GetMapping("/accounts/{id}/messages/{messageId}")
    public MailDetail detail(@PathVariable String id, @PathVariable int messageId) throws Exception {
        return withMessage(id, messageId, message -> {
            MailContentExtractor.Content content = MailContentExtractor.read(message);
            return new MailDetail(content.text(), content.html(), content.attachments());
        });
    }

    /**
     * 下载指定附件。服务端最多缓冲 25 MB，超过限制即报错；响应禁止缓存，
     * 文件名由详情接口返回，前端在发起下载时设置本地文件名。
     */
    @GetMapping("/accounts/{id}/messages/{messageId}/attachments/{index}")
    public ResponseEntity<byte[]> download(@PathVariable String id, @PathVariable int messageId,
                                           @PathVariable int index) throws Exception {
        byte[] bytes = withMessage(id, messageId, message -> {
            Part part = MailContentExtractor.attachment(message, index);
            try (InputStream input = part.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > 25 * 1024 * 1024)
                        throw new IllegalArgumentException("附件超过 25 MB，无法下载");
                    output.write(buffer, 0, count);
                }
                return output.toByteArray();
            }
        });
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_OCTET_STREAM).body(bytes);
    }

    /**
     * 使详情读取和附件读取共享连接生命周期，读取逻辑保持在文件夹打开期间执行。
     */
    @FunctionalInterface
    private interface MessageReader<T> {
        T read(Message message) throws Exception;
    }

    /**
     * 在只读 INBOX 中读取指定邮件，并在完成后关闭文件夹与连接。
     */
    private <T> T withMessage(String accountId, int messageId, MessageReader<T> reader) throws Exception {
        if (messageId < 1) throw new IllegalArgumentException("邮件不存在");
        Account a = account(accountId);
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.connectiontimeout", "10000");
        props.put("mail.imaps.timeout", "10000");
        IMAPStore store = (IMAPStore) Session.getInstance(props).getStore("imaps");
        try {
            store.connect(a.settings().imapHost(), a.settings().imapPort(), a.email(), a.password());
            ImapClientIdentification.identify(a.provider(), store);
            Folder folder = store.getFolder("INBOX");
            folder.open(Folder.READ_ONLY);
            try {
                if (messageId > folder.getMessageCount()) throw new IllegalArgumentException("邮件不存在");
                return reader.read(folder.getMessage(messageId));
            } finally {
                folder.close(false);
            }
        } finally {
            store.close();
        }
    }

    /**
     * 无附件时接受 JSON，请求仍走统一的发信校验与批量处理。
     */
    @PostMapping(value = "/send", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> send(@RequestBody SendInput input) throws Exception {
        return sendInternal(input, new MultipartFile[0]);
    }

    /**
     * 有附件时接受 multipart/form-data，字段含义与 JSON 发信接口一致。
     */
    @PostMapping(value = "/send", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> sendMultipart(@RequestParam String accountId,
                                             @RequestParam String recipients,
                                             @RequestParam(required = false) String cc,
                                             @RequestParam String subject,
                                             @RequestParam String body,
                                             @RequestParam(defaultValue = "text") String format,
                                             @RequestPart(value = "attachments", required = false) MultipartFile[] attachments) throws Exception {
        return sendInternal(new SendInput(accountId, recipients, cc, subject, body, format), attachments);
    }

    /**
     * 校验收件人、格式和附件大小，再逐人发送并汇总成功与失败。
     */
    private Map<String, Object> sendInternal(SendInput input, MultipartFile[] attachments) throws Exception {
        Account a = account(input.accountId());
        List<String> recipients = RecipientParser.parse(input.recipients());
        List<String> cc = input.cc() == null || input.cc().isBlank() ? List.of() : RecipientParser.parse(input.cc());
        if (recipients.stream().anyMatch(cc::contains)) throw new IllegalArgumentException("收件人和抄送地址不能重复");
        if (input.subject() == null || input.subject().isBlank()) throw new IllegalArgumentException("请填写主题");
        String format = input.format() == null || input.format().isBlank() ? "text" : input.format().toLowerCase(Locale.ROOT);
        if (!format.equals("text") && !format.equals("html"))
            throw new IllegalArgumentException("邮件格式仅支持 text 或 html");
        if (input.body() == null || input.body().isBlank()) throw new IllegalArgumentException("请填写邮件正文");
        MultipartFile[] files = attachments == null ? new MultipartFile[0] : attachments;
        if (files.length > 10) throw new IllegalArgumentException("单次最多上传 10 个附件");
        long totalSize = 0;
        for (MultipartFile file : files) {
            if (file.isEmpty() || file.getOriginalFilename() == null || file.getOriginalFilename().isBlank())
                throw new IllegalArgumentException("附件不能为空且必须有文件名");
            if (file.getSize() > 10L * 1024 * 1024) throw new IllegalArgumentException("单个附件不能超过 10 MB");
            totalSize += file.getSize();
        }
        if (totalSize > 25L * 1024 * 1024) throw new IllegalArgumentException("附件总大小不能超过 25 MB");
        List<String> sent = new ArrayList<>();
        Map<String, String> failed = new LinkedHashMap<>();
        // 每位收件人独立发送；单人失败不影响其他人，并向前端返回逐人结果。
        for (String recipient : recipients) {
            try {
                if (sender.send(a, recipient, cc, input.subject(), input.body(), format, files)) sent.add(recipient);
                else failed.put(recipient, "发送失败，请检查账号授权、SMTP 配置或服务商限额");
            } catch (Exception e) {
                log.warn("Mail send failed for {} using {}", recipient, a.email(), e);
                failed.put(recipient, "发送失败，请检查账号授权、SMTP 配置或服务商限额");
            }
        }
        return Map.of("sent", sent, "failed", failed);
    }

    /**
     * 用户输入、分页参数或邮件/附件序号无效时返回 400。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badRequest(IllegalArgumentException e) {
        return Map.of("error", e.getMessage());
    }

    /**
     * Spring 在进入控制器前拦截过大的 multipart 请求。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public Map<String, String> tooLarge(MaxUploadSizeExceededException e) {
        return Map.of("error", "附件超过上传大小限制：单个 10 MB，总计 25 MB");
    }

    /**
     * 数据库异常使用明确提示，同时避免向浏览器暴露内部连接信息。
     */
    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String, String> database(DataAccessException e) {
        return Map.of("error", "数据库连接或读写失败，请检查 MySQL 配置和权限");
    }

    /**
     * 授权码解密或账号配置读取失败时提示检查服务端配置。
     */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String, String> configuration(IllegalStateException e) {
        return Map.of("error", "账号配置读取失败，请检查数据库或加密密钥");
    }

    /**
     * 邮箱服务端或网络异常统一记录日志，响应不包含敏感异常细节。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String, String> upstream(Exception e) {
        log.error("Mail operation failed: {}", e.toString(), e);
        return Map.of("error", "邮箱服务器连接失败，请检查授权码、服务器设置和网络连接");
    }
}
