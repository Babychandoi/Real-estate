package com.company.bds.iam.api;

import com.company.bds.iam.application.AuthService;
import com.company.bds.iam.application.MfaService;
import com.company.bds.shared.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;
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
    private final MfaService mfa;
    private final ClientContexts clients;

    public AuthController(AuthService authService, MfaService mfa, ClientContexts clients) {
        this.authService = authService;
        this.mfa = mfa;
        this.clients = clients;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthService.RegistrationResult> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(
                request.email(), request.password(), request.name(), request.accountType(), request.returnTo()));
    }

    /** Old e-mail links: kept for compatibility; the page now posts the token in the body (never in a URL we log). */
    @GetMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-email")
    public AuthService.VerificationResult verifyEmailPost(@Valid @RequestBody TokenRequest request) {
        return authService.verifyEmail(request.token());
    }

    /** State of a password-reset link (VALID, EXPIRED, USED, SUPERSEDED, INVALID) before the form is shown. */
    @PostMapping("/password-reset/status")
    public TokenStatusResponse passwordResetStatus(@Valid @RequestBody TokenRequest request) {
        return new TokenStatusResponse(authService.passwordResetTokenStatus(request.token()).name());
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
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest http) {
        authService.resetPassword(request.token(), request.password(), clients.of(http));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public AuthService.AuthResult login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request.email(), request.password(), clients.of(http));
    }

    /** Staff portal: a session, or {@code mfaRequired=true} with a challenge for the steps below (UI-17). */
    @PostMapping("/admin/login")
    public AuthService.AdminLoginResult adminLogin(@Valid @RequestBody AdminLoginRequest request, HttpServletRequest http) {
        return authService.adminLogin(request.email(), request.password(), clients.of(http));
    }

    @PostMapping("/admin/mfa/verify")
    public AuthService.AuthResult verifyMfa(@Valid @RequestBody MfaVerifyRequest request, HttpServletRequest http) {
        boolean hasCode = request.code() != null && !request.code().isBlank();
        boolean hasRecovery = request.recoveryCode() != null && !request.recoveryCode().isBlank();
        if (hasCode == hasRecovery) {
            throw ApiException.badRequest("MFA_CODE_REQUIRED", "Nhập mã 6 số từ ứng dụng xác thực hoặc một mã khôi phục.");
        }
        return authService.signedIn(mfa.verify(request.challengeToken(), request.code(), request.recoveryCode(), clients.of(http)));
    }

    @PostMapping("/admin/mfa/enroll")
    public MfaService.Enrollment beginEnrollment(@Valid @RequestBody ChallengeRequest request) {
        return mfa.beginEnrollment(request.challengeToken());
    }

    @PostMapping("/admin/mfa/enroll/confirm")
    public EnrollmentCompleted confirmEnrollment(@Valid @RequestBody MfaConfirmRequest request, HttpServletRequest http) {
        MfaService.Completed completed = mfa.confirmEnrollment(request.challengeToken(), request.code(), clients.of(http));
        return new EnrollmentCompleted(authService.signedIn(completed.session()), completed.recoveryCodes());
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

    @PutMapping("/me/avatar")
    public AuthService.UserView updateAvatar(@Valid @RequestBody UpdateAvatarRequest request, Authentication authentication) {
        return authService.updateAvatar(CurrentUser.id(authentication), request.avatarMediaUrl());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        authService.logout(bearer(authorization));
        return ResponseEntity.noContent().build();
    }

    private String bearer(String header) {
        return header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
    }

    public record TokenRequest(@NotBlank @Size(min=32, max=128) String token) {}
    public record TokenStatusResponse(String status) {}
    public record ChallengeRequest(@NotBlank @Size(max=128) String challengeToken) {}
    public record MfaVerifyRequest(@NotBlank @Size(max=128) String challengeToken,
                                   @Pattern(regexp="^\\s*\\d{3}\\s?\\d{3}\\s*$") String code,
                                   @Size(max=32) String recoveryCode) {}
    public record MfaConfirmRequest(@NotBlank @Size(max=128) String challengeToken,
                                    @NotBlank @Pattern(regexp="^\\s*\\d{3}\\s?\\d{3}\\s*$") String code) {}
    public record EnrollmentCompleted(AuthService.AuthResult session, java.util.List<String> recoveryCodes) {}
    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
    public record AdminLoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
    public record RegisterRequest(@NotBlank @Email String email,
                                  @NotBlank @Size(min=10, max=72) String password,
                                  @NotBlank @Size(min=2, max=150) String name,
                                  @Pattern(regexp="USER|OWNER|BROKER") String accountType,
                                  @Size(max=512) String returnTo) {}
    public record ResendVerificationRequest(@NotBlank @Email String email) {}
    public record ForgotPasswordRequest(@NotBlank @Email String email) {}
    public record ResetPasswordRequest(@NotBlank @Size(min=32, max=128) String token,
                                       @NotBlank @Size(min=10, max=72) String password) {}
    public record UpdateAvatarRequest(@Size(max=1000) String avatarMediaUrl) {}
    public record UpdateProfileRequest(@NotBlank @Size(min=2, max=150) String name,
                                       @NotBlank @Pattern(regexp="^(0|\\+84)[35789][0-9]{8}$") String phone,
                                       @Size(max=1000) String avatarMediaUrl) {}
}
