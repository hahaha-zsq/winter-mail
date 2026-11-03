package com.winter.wintermail.mail;

import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.SQLException;

/**
 * 首次访问账号表时执行幂等建表，后续请求直接跳过。
 */
@Component
public class AccountSchema {
    private final DataSource dataSource;
    private volatile boolean initialized;

    public AccountSchema(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * synchronized 与 initialized 配合，避免并发请求重复建表。
     */
    public synchronized void ensureTable() {
        if (initialized) return;
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            // MySQL 自带的 IF NOT EXISTS 使多实例首次建表也保持幂等。
            statement.execute("CREATE TABLE IF NOT EXISTS mail_accounts (id VARCHAR(36) PRIMARY KEY, email VARCHAR(320) NOT NULL, credential TEXT NOT NULL, provider VARCHAR(30) NOT NULL, imap_host VARCHAR(255) NOT NULL, imap_port INT NOT NULL, smtp_host VARCHAR(255) NOT NULL, smtp_port INT NOT NULL, smtp_ssl BOOLEAN NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            // 只有建表成功后才标记完成，失败时下一次请求仍可重试。
            initialized = true;
        } catch (SQLException e) {
            throw new IllegalStateException("初始化邮箱账号表失败", e);
        }
    }
}
