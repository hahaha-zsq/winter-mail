package com.winter.wintermail.mail;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import java.util.Comparator;
import java.util.List;

/** 在数据库实体与业务账号之间转换，并统一处理授权码的加解密。 */
@Repository
public class AccountRepository {
    /** 业务模型中的 password 是解密后的授权码，只在服务端内部流转。 */
    public record Account(String id, String email, String password, String provider, ProviderSettings settings) {}
    private final AccountMapper mapper;
    private final CredentialCipher cipher;
    private final AccountSchema schema;
    @Autowired public AccountRepository(AccountMapper mapper, CredentialCipher cipher, AccountSchema schema) {
        this.mapper = mapper; this.cipher = cipher; this.schema = schema;
    }
    AccountRepository(AccountMapper mapper, CredentialCipher cipher) { this(mapper, cipher, null); }
    /** 首次访问时确保表存在；测试可用不带 schema 的构造函数注入 Mapper。 */
    private void ensureTable() { if (schema != null) schema.ensureTable(); }
    /** 仅在业务需要连接邮件服务器时解密授权码，不向前端返回此对象。 */
    private Account toAccount(AccountEntity entity) {
        if (entity == null) return null;
        return new Account(entity.getId(), entity.getEmail(), cipher.decrypt(entity.getCredential()), entity.getProvider(), new ProviderSettings(entity.getImapHost(), entity.getImapPort(), entity.getSmtpHost(), entity.getSmtpPort(), Boolean.TRUE.equals(entity.getSmtpSsl())));
    }
    /** 查询所有账号并按邮箱排序，保持前端账号顺序稳定。 */
    public List<Account> findAll() {
        ensureTable();
        return mapper.selectList(null).stream().map(this::toAccount).sorted(Comparator.comparing(Account::email)).toList();
    }
    /** 查询单个账号；没有记录时返回 null，由 API 层生成错误响应。 */
    public Account find(String id) { ensureTable(); return toAccount(mapper.selectById(id)); }
    /** 保存前加密授权码；IMAP/SMTP 设置与账号一起持久化。 */
    public void save(Account account) {
        ensureTable();
        AccountEntity entity = new AccountEntity();
        entity.setId(account.id()); entity.setEmail(account.email()); entity.setCredential(cipher.encrypt(account.password())); entity.setProvider(account.provider());
        entity.setImapHost(account.settings().imapHost()); entity.setImapPort(account.settings().imapPort()); entity.setSmtpHost(account.settings().smtpHost()); entity.setSmtpPort(account.settings().smtpPort()); entity.setSmtpSsl(account.settings().smtpSsl());
        mapper.insert(entity);
    }
    /** 只移除数据库配置，不触碰 IMAP 服务器上的邮件。 */
    public void delete(String id) { ensureTable(); mapper.deleteById(id); }
}
