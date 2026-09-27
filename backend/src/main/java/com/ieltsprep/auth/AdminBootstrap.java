package com.ieltsprep.auth;

import com.ieltsprep.config.AppProperties;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the bootstrap admin from ADMIN_EMAIL / ADMIN_PASSWORD on first start. The password is stored only as a
 * BCrypt hash; changing ADMIN_PASSWORD later does not overwrite a password changed in the app.
 */
@Component
@Order(1)
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final AppProperties props;
    private final Clock clock;

    public AdminBootstrap(AppUserRepository users, PasswordEncoder encoder, AppProperties props, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.props = props;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.Admin admin = props.admin();
        if (admin == null || admin.email() == null || admin.email().isBlank()) {
            if (users.count() == 0) {
                log.warn("No users exist and ADMIN_EMAIL is not set — set ADMIN_EMAIL and ADMIN_PASSWORD in .env to log in.");
            }
            return;
        }
        if (users.findByEmailIgnoreCase(admin.email().trim()).isPresent()) {
            return;
        }
        if (admin.password() == null || admin.password().isBlank()) {
            log.warn("ADMIN_EMAIL is set but ADMIN_PASSWORD is empty — admin not created.");
            return;
        }
        AppUser user = new AppUser();
        user.setEmail(admin.email().trim());
        user.setPasswordHash(encoder.encode(admin.password()));
        user.setRole("ADMIN");
        user.setCreatedAt(Instant.now(clock));
        users.save(user);
        log.info("Created bootstrap admin {}", user.getEmail());
    }
}
