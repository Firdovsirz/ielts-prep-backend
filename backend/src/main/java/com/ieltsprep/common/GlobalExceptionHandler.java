package com.ieltsprep.common;

import com.ieltsprep.claude.ClaudeException;
import com.ieltsprep.claude.SpendCapExceededException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ApiError(String code, String message, Instant timestamp) {
        static ApiError of(String code, String message) {
            return new ApiError(code, message, Instant.now());
        }
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> api(ApiException e) {
        return ResponseEntity.status(e.status()).body(ApiError.of(e.code(), e.getMessage()));
    }

    @ExceptionHandler(SpendCapExceededException.class)
    ResponseEntity<ApiError> spendCap(SpendCapExceededException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError.of("SPEND_CAP_REACHED", e.getMessage()));
    }

    @ExceptionHandler(ClaudeException.class)
    ResponseEntity<ApiError> claude(ClaudeException e) {
        log.warn("Claude call failed: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiError.of(e.code(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .findFirst()
                .orElse("Invalid request");
        return ResponseEntity.badRequest().body(ApiError.of("VALIDATION", message));
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> auth(AuthenticationException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiError.of("UNAUTHORIZED", e.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> denied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.of("FORBIDDEN", e.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> noResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of("NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e) {
        log.error("Unhandled error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("INTERNAL", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
    }
}
