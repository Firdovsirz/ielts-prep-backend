package com.ieltsprep.auth;

import com.ieltsprep.common.ApiException;
import com.ieltsprep.config.AppProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    public record TokenResponse(String token, Instant expiresAt, String email, String role) {}

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final JwtEncoder jwtEncoder;
    private final AppProperties props;
    private final Clock clock;

    public AuthService(AppUserRepository users, PasswordEncoder encoder, JwtEncoder jwtEncoder, AppProperties props,
            Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.jwtEncoder = jwtEncoder;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public TokenResponse login(String email, String password) {
        AppUser user = users.findByEmailIgnoreCase(email == null ? "" : email.trim())
                .filter(u -> encoder.matches(password == null ? "" : password, u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Incorrect email or password"));
        user.setLastLoginAt(Instant.now(clock));
        return issue(user);
    }

    /** A fresh token for the user (the subject is the e-mail, so a new one is needed after the e-mail changes). */
    TokenResponse issue(AppUser user) {
        Instant now = Instant.now(clock);
        Instant expires = now.plus(Math.max(1, props.security().tokenTtlHours()), ChronoUnit.HOURS);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("ielts-prep")
                .subject(user.getEmail())
                .issuedAt(now)
                .expiresAt(expires)
                .claim("role", user.getRole())
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new TokenResponse(token, expires, user.getEmail(), user.getRole());
    }

    @Transactional
    public void changePassword(String email, String current, String next) {
        updateAccount(email, current, null, next);
    }

    /**
     * Changes the signed-in user's e-mail and/or password (the current password is required). Returns a new token,
     * because the token's subject is the e-mail.
     */
    @Transactional
    public TokenResponse updateAccount(String currentEmail, String currentPassword, String newEmail, String newPassword) {
        AppUser user = users.findByEmailIgnoreCase(currentEmail).orElseThrow(() -> ApiException.notFound("User"));
        if (currentPassword == null || !encoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_CREDENTIALS", "Current password is incorrect");
        }
        if (newEmail != null && !newEmail.isBlank()) {
            String email = normaliseEmail(newEmail);
            ensureFree(email, user);
            user.setEmail(email);
        }
        if (newPassword != null && !newPassword.isEmpty()) {
            user.setPasswordHash(encoder.encode(validPassword(newPassword)));
        }
        return issue(user);
    }

    /**
     * Sets the admin login to the given e-mail and password without knowing the old password — for the server
     * operator when the login is lost (./deploy.sh --reset-admin → --task=reset-admin). Creates the admin if there
     * is none.
     */
    @Transactional
    public String resetAdmin(String newEmail, String newPassword) {
        String email = normaliseEmail(newEmail);
        String hash = encoder.encode(validPassword(newPassword));
        AppUser admin = users.findAll(Sort.by("id")).stream().filter(u -> "ADMIN".equals(u.getRole())).findFirst().orElseGet(() -> {
            AppUser u = new AppUser();
            u.setRole("ADMIN");
            u.setCreatedAt(Instant.now(clock));
            return u;
        });
        ensureFree(email, admin);
        admin.setEmail(email);
        admin.setPasswordHash(hash);
        users.save(admin);
        return email;
    }

    private void ensureFree(String email, AppUser owner) {
        users.findByEmailIgnoreCase(email).filter(u -> !u.getId().equals(owner.getId())).ifPresent(u -> {
            throw ApiException.conflict("Another account already uses " + email);
        });
    }

    static String normaliseEmail(String email) {
        String e = email == null ? "" : email.trim();
        int at = e.indexOf('@');
        if (e.length() > 255 || at < 1 || at != e.lastIndexOf('@') || e.indexOf('.', at) < at + 2 || e.endsWith(".")
                || e.chars().anyMatch(Character::isWhitespace)) {
            throw ApiException.badRequest("Enter a valid e-mail address");
        }
        return e;
    }

    static String validPassword(String password) {
        if (password == null || password.length() < 8) {
            throw ApiException.badRequest("The password must be at least 8 characters");
        }
        if (password.length() > 200) {
            throw ApiException.badRequest("The password is too long");
        }
        return password;
    }
}
