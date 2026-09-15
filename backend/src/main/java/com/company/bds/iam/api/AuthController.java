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
import com.company.bds.shared.security.CurrentUser;
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

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.requestPasswordReset(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.password());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public AuthService.AuthResult login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password());
    }

    @PostMapping("/admin/login")
    public AuthService.AuthResult adminLogin(@Valid @RequestBody AdminLoginRequest request) {
        return authService.adminLogin(request.email(), request.password());
    }

    @GetMapping("/me")
    public AuthService.UserView me(Authentication authentication) {
        AuthService.UserAccount user = (AuthService.UserAccount) authentication.getPrincipal();
        return authService.view(user);
    }

    @PutMapping("/me")
    public AuthService.UserView updateMe(@Valid @RequestBody UpdateProfileRequest request, Authentication authentication) {
        return authService.updateProfile(CurrentUser.id(authentication), request.name(), request.phone(), request.avatarMediaUrl());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        authService.logout(bearer(authorization));
        return ResponseEntity.noContent().build();
    }

    private String bearer(String header) {
        return header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
    public record AdminLoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
    public record RegisterRequest(@NotBlank @Email String email,
                                  @NotBlank @Size(min=10, max=72) String password,
                                  @NotBlank @Size(min=2, max=150) String name,
                                  @Pattern(regexp="USER|BROKER") String accountType) {}
    public record ResendVerificationRequest(@NotBlank @Email String email) {}
    public record ForgotPasswordRequest(@NotBlank @Email String email) {}
    public record ResetPasswordRequest(@NotBlank @Size(min=32, max=128) String token,
                                       @NotBlank @Size(min=10, max=72) String password) {}
    public record UpdateProfileRequest(@NotBlank @Size(min=2, max=150) String name,
                                       @NotBlank @Pattern(regexp="^(0|\\+84)[35789][0-9]{8}$") String phone,
                                       @Size(max=1000) String avatarMediaUrl) {}
}
