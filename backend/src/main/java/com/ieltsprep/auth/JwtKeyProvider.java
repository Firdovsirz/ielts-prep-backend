package com.ieltsprep.auth;

import com.ieltsprep.config.AppProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * HMAC key for signing login tokens. Uses JWT_SECRET when set; otherwise generates one and persists it under the
 * data directory so tokens survive restarts.
 */
@Component
public class JwtKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyProvider.class);

    private final SecretKey key;

    public JwtKeyProvider(AppProperties props) {
        String secret = props.security() == null ? null : props.security().jwtSecret();
        if (secret == null || secret.isBlank()) {
            secret = persistedSecret(props.dataPath().resolve(".jwt-secret"));
        }
        this.key = new SecretKeySpec(sha256(secret), "HmacSHA256");
    }

    public SecretKey key() {
        return key;
    }

    private static String persistedSecret(Path file) {
        try {
            if (Files.exists(file)) {
                return Files.readString(file, StandardCharsets.UTF_8).trim();
            }
            byte[] bytes = new byte[48];
            new SecureRandom().nextBytes(bytes);
            String secret = Base64.getEncoder().encodeToString(bytes);
            Files.createDirectories(file.getParent());
            Files.writeString(file, secret, StandardCharsets.UTF_8);
            log.info("Generated a JWT signing secret at {} (set JWT_SECRET to override)", file);
            return secret;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read/write " + file, e);
        }
    }

    /** Derives a 256-bit key from any secret string, so short secrets still satisfy HS256. */
    private static byte[] sha256(String secret) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
