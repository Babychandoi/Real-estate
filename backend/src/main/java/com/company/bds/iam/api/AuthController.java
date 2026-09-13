package com.company.bds.iam.api;

import com.company.bds.iam.application.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) { this.authService = authService; }

    @PostMapping("/register")
    public ResponseEntity<AuthService.RegistrationResult> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(
                request.email(), request.password(), request.name(), request.accountType()));
    }

    @GetMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/login")
    public AuthService.AuthResult login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password(), request.mfaCode());
    }

    @GetMapping("/me")
    public AuthService.UserView me(Authentication authentication) {
        AuthService.UserAccount user = (AuthService.UserAccount) authentication.getPrincipal();
        return authService.view(user);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        authService.logout(bearer(authorization));
        return ResponseEntity.noContent().build();
    }

    private String bearer(String header) {
        return header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password,
                               @Pattern(regexp="^$|\\d{6}") String mfaCode) {}
    public record RegisterRequest(@NotBlank @Email String email,
                                  @NotBlank @Size(min=10, max=72) String password,
                                  @NotBlank @Size(min=2, max=150) String name,
                                  @Pattern(regexp="USER|BROKER") String accountType) {}
    public record ResendVerificationRequest(@NotBlank @Email String email) {}
}
