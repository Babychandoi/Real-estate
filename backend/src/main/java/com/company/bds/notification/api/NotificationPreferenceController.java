package com.company.bds.notification.api;

import com.company.bds.notification.application.NotificationPreferenceService;
import com.company.bds.notification.application.NotificationPreferenceService.Preference;
import com.company.bds.notification.application.UnsubscribeService;
import com.company.bds.notification.domain.NotificationCategory;
import com.company.bds.shared.error.ApiException;
import com.company.bds.shared.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Notification preferences of the signed-in user, and the public one-click unsubscribe endpoint. */
@RestController
public class NotificationPreferenceController {
    private static final Supplier<ApiException> GONE = () -> new ApiException(HttpStatus.NOT_FOUND,
            "UNSUBSCRIBE_LINK_INVALID", "Liên kết hủy nhận thông báo không hợp lệ hoặc đã hết hạn.");

    private final NotificationPreferenceService preferences;
    private final UnsubscribeService unsubscribe;

    public NotificationPreferenceController(NotificationPreferenceService preferences, UnsubscribeService unsubscribe) {
        this.preferences = preferences;
        this.unsubscribe = unsubscribe;
    }

    @GetMapping("/api/v1/me/notification-preferences")
    public List<PreferenceDto> list(Authentication auth) {
        return preferences.list(CurrentUser.id(auth)).stream().map(PreferenceDto::of).toList();
    }

    @PutMapping("/api/v1/me/notification-preferences")
    public List<PreferenceDto> update(Authentication auth, @RequestBody List<PreferenceDto> body) {
        if (body == null || body.isEmpty() || body.size() > NotificationCategory.values().length) {
            throw ApiException.badRequest("INVALID_PREFERENCES", "Danh sách tùy chọn thông báo không hợp lệ.");
        }
        UUID userId = CurrentUser.id(auth);
        List<Preference> changes = new ArrayList<>();
        Set<NotificationCategory> seen = new HashSet<>();
        for (PreferenceDto item : body) {
            NotificationCategory category;
            try {
                category = NotificationCategory.parse(item.category());
            } catch (IllegalArgumentException ex) {
                throw ApiException.badRequest("INVALID_PREFERENCES", "Loại thông báo không hợp lệ.");
            }
            if (!seen.add(category)) throw ApiException.badRequest("INVALID_PREFERENCES", "Mỗi loại thông báo chỉ một lần.");
            if (category.mandatoryInApp() && !item.inApp()) {
                throw ApiException.badRequest("IN_APP_REQUIRED",
                        "Thông báo trong ứng dụng về tài khoản và tin đăng của bạn không thể tắt.");
            }
            changes.add(new Preference(category, item.inApp(), item.email(), category.mandatoryInApp()));
        }
        return preferences.update(userId, changes).stream().map(PreferenceDto::of).toList();
    }

    /** What an unsubscribe link does (the confirmation page shows it before the user confirms). */
    @GetMapping("/api/v1/public/unsubscribe")
    public UnsubscribeDto describe(@RequestParam("token") String token) {
        return unsubscribe.describe(token).map(UnsubscribeDto::of).orElseThrow(GONE);
    }

    /** One-click unsubscribe (RFC 8058 POST from the mail client, or the confirmation page). Idempotent. */
    @PostMapping("/api/v1/public/unsubscribe")
    public UnsubscribeDto apply(@RequestParam("token") String token) {
        return unsubscribe.apply(token).map(UnsubscribeDto::of).orElseThrow(GONE);
    }

    public record PreferenceDto(String category, boolean inApp, boolean email, boolean mandatoryInApp) {
        static PreferenceDto of(Preference p) {
            return new PreferenceDto(p.category().name(), p.inApp(), p.email(), p.mandatoryInApp());
        }
    }

    public record UnsubscribeDto(String scope, String category, String savedSearchName, boolean applied) {
        static UnsubscribeDto of(UnsubscribeService.Target t) {
            return new UnsubscribeDto(t.scope(), t.category(), t.savedSearchName(), t.applied());
        }
    }
}
