package com.company.bds.notification.application;

import com.company.bds.notification.application.port.NotificationStorePort;
import com.company.bds.notification.application.port.NotificationStorePort.ChannelChoice;
import com.company.bds.notification.domain.NotificationCategory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Channel choices per category; a category without an explicit choice uses its defaults. */
@Service
public class NotificationPreferenceService {
    private final NotificationStorePort store;

    public NotificationPreferenceService(NotificationStorePort store) {
        this.store = store;
    }

    /** Effective choice: a mandatory category always keeps in-app delivery. */
    public ChannelChoice choice(UUID userId, NotificationCategory category) {
        ChannelChoice explicit = store.preferences(userId).get(category);
        return effective(category, explicit);
    }

    @Transactional(readOnly = true)
    public List<Preference> list(UUID userId) {
        Map<NotificationCategory, ChannelChoice> explicit = store.preferences(userId);
        List<Preference> out = new ArrayList<>();
        for (NotificationCategory category : NotificationCategory.values()) {
            ChannelChoice choice = effective(category, explicit.get(category));
            out.add(new Preference(category, choice.inApp(), choice.email(), category.mandatoryInApp()));
        }
        return out;
    }

    /** Saves the given choices (others unchanged). In-app cannot be turned off for a mandatory category. */
    @Transactional
    public List<Preference> update(UUID userId, List<Preference> changes) {
        for (Preference change : changes) {
            boolean inApp = change.category().mandatoryInApp() || change.inApp();
            store.savePreference(userId, change.category(), inApp, change.email());
        }
        return list(userId);
    }

    /** Turns e-mail off for one category (unsubscribe link), keeping the in-app choice. */
    @Transactional
    public void disableEmail(UUID userId, NotificationCategory category) {
        ChannelChoice current = choice(userId, category);
        store.savePreference(userId, category, current.inApp(), false);
    }

    private static ChannelChoice effective(NotificationCategory category, ChannelChoice explicit) {
        boolean inApp = explicit == null ? category.defaultInApp() : explicit.inApp();
        boolean email = explicit == null ? category.defaultEmail() : explicit.email();
        return new ChannelChoice(inApp || category.mandatoryInApp(), email);
    }

    public record Preference(NotificationCategory category, boolean inApp, boolean email, boolean mandatoryInApp) {}
}
