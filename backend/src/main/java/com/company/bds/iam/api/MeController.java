package com.company.bds.iam.api;

import com.company.bds.iam.application.AuthService;
import com.company.bds.shared.security.CurrentUser;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Account actions of the signed-in user. */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {
    private final AuthService auth;

    public MeController(AuthService auth) { this.auth = auth; }

    /**
     * USER → OWNER (P-09). The body must carry {@code "confirmOwnProperty": true}: the user states they post their own
     * property (not as a broker). Answers 200 for an upgrade and for an account that already is OWNER.
     */
    @PostMapping("/become-owner")
    public ResponseEntity<AuthService.BecomeOwnerResult> becomeOwner(@RequestBody(required = false) BecomeOwnerRequest request,
                                                                    Authentication authentication) {
        if (request == null || !Boolean.TRUE.equals(request.confirmOwnProperty())) {
            throw new IllegalArgumentException(
                    "Xác nhận bạn là chủ sở hữu (hoặc được chủ sở hữu ủy quyền) của bất động sản sẽ đăng.");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(auth.becomeOwner(CurrentUser.id(authentication)));
    }

    public record BecomeOwnerRequest(Boolean confirmOwnProperty) {}
}
