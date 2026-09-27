package com.ieltsprep.auth;

import com.ieltsprep.common.ApiException;
import com.ieltsprep.config.AppProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
        Instant now = Instant.now(clock);
        user.setLastLoginAt(now);
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
        AppUser user = users.findByEmailIgnoreCase(email).orElseThrow(() -> ApiException.notFound("User"));
        if (!encoder.matches(current, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_CREDENTIALS", "Current password is incorrect");
        }
        if (next == null || next.length() < 8) {
            throw ApiException.badRequest("New password must be at least 8 characters");
        }
        user.setPasswordHash(encoder.encode(next));
    }
}
