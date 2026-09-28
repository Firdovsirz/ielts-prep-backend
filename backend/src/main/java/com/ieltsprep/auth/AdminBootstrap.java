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
 * Creates the bootstrap admin from ADMIN_EMAIL / ADMIN_PASSWORD on first start (when no account exists). The password
 * is stored only as a BCrypt hash. Later changes to those variables do not touch the account: change the login in
 * Settings → Account, or reset it with ./deploy.sh --reset-admin.
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
        if (users.count() > 0) {
            // The login is created once. Editing ADMIN_EMAIL/ADMIN_PASSWORD later does not add or change accounts
            // (that would leave a second admin behind); the operator resets it explicitly instead.
            if (users.findByEmailIgnoreCase(admin.email().trim()).isEmpty()) {
                log.info("ADMIN_EMAIL does not match the existing login. To change the login run ./deploy.sh --reset-admin "
                        + "(or --task=reset-admin), or change it in Settings → Account.");
            }
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
