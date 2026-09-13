package com.company.bds.iam.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@ConditionalOnProperty(name="app.mode", havingValue="demo")
public class DemoAccountInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final String password;

    public DemoAccountInitializer(JdbcTemplate jdbc, PasswordEncoder encoder,
                                  @Value("${app.demo.password}") String password) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        if (password == null || password.length() < 12) {
            throw new IllegalStateException("DEMO_ACCOUNT_PASSWORD must contain at least 12 characters in demo mode");
        }
        this.password = password;
    }

    @Override @Transactional
    public void run(ApplicationArguments args) {
        seed("00000000-0000-0000-0000-000000000001", "demo.user@bds.local", "Người dùng Demo", "USER");
        seed("00000000-0000-0000-0000-000000000002", "demo.broker@bds.local", "Môi giới Demo", "BROKER");
        seed("00000000-0000-0000-0000-000000000003", "demo.moderator@bds.local", "Kiểm duyệt Demo", "MODERATOR");
        seed("00000000-0000-0000-0000-000000000004", "demo.admin@bds.local", "Quản trị Demo", "ADMIN");
    }

    private void seed(String idText, String email, String name, String role) {
        UUID id = UUID.fromString(idText);
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id=?", Integer.class, id);
        if (count != null && count > 0) {
            jdbc.update("UPDATE users SET full_name=?,email=?,password_hash=?,status='ACTIVE' WHERE id=?",
                    name, email, encoder.encode(password), id);
            jdbc.update("DELETE FROM user_roles WHERE user_id=?", id);
            jdbc.update("INSERT INTO user_roles(user_id,role) VALUES (?,?)", id, role);
            return;
        }
        jdbc.update("""
                INSERT INTO users(id,phone_lookup_hash,phone_encrypted,full_name,email,password_hash,status)
                VALUES (?,?,?,?,?,?,'ACTIVE')
                """, id, AuthService.sha256("demo:" + id), "DEMO_ONLY", name, email, encoder.encode(password));
        jdbc.update("INSERT INTO user_roles(user_id,role) VALUES (?,?)", id, role);
    }
}
