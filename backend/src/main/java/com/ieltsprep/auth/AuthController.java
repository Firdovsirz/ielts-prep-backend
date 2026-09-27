package com.ieltsprep.auth;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth")
public class AuthController {

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {}

    public record Me(String email, String role) {}

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    public AuthService.TokenResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req.email(), req.password());
    }

    @GetMapping("/me")
    public Me me(@AuthenticationPrincipal Jwt jwt) {
        return new Me(jwt.getSubject(), jwt.getClaimAsString("role"));
    }

    @PostMapping("/password")
    public void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest req) {
        auth.changePassword(jwt.getSubject(), req.currentPassword(), req.newPassword());
    }
}
