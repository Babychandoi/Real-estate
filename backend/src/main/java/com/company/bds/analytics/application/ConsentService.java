package com.company.bds.analytics.application;

import com.company.bds.analytics.application.port.out.ConsentRecordRepository;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Records analytics consent decisions (Decree 13/2023/NĐ-CP Art. 11: consent must be specific, voluntary and
 * demonstrable; withdrawal must be possible at any time). Each decision is an append-only record: the random consent
 * id the browser generated, the choice, the policy version shown and, for signed-in visitors, the user id from the
 * bearer token. No IP address or user agent is stored.
 */
@Service
public class ConsentService {
    public static final String PURPOSE_ANALYTICS = "analytics";
    private static final Pattern CONSENT_ID = Pattern.compile("[A-Za-z0-9_-]{8,64}");
    private static final Pattern POLICY_VERSION = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}(\\.[0-9]{1,3})?");
    private static final Set<String> CHOICES = Set.of("granted", "withdrawn");
    private static final Set<String> SOURCES = Set.of("banner", "preferences");

    public record ConsentCommand(@Nullable String consentId, @Nullable String purpose, @Nullable String choice,
                                 @Nullable String policyVersion, @Nullable String source) {}

    public record ConsentReceipt(UUID recordId, Instant recordedAt) {}

    private final ConsentRecordRepository records;
    private final Clock clock;

    public ConsentService(ConsentRecordRepository records, Clock clock) {
        this.records = records;
        this.clock = clock;
    }

    @Transactional
    public ConsentReceipt record(ConsentCommand command, @Nullable UUID userId) {
        List<EventViolation> errors = new ArrayList<>();
        if (command.consentId() == null || !CONSENT_ID.matcher(command.consentId()).matches()) {
            errors.add(new EventViolation("consentId", "INVALID_CONSENT_ID", "consentId phải gồm 8–64 ký tự chữ, số, '-' hoặc '_'."));
        }
        String purpose = command.purpose() == null ? PURPOSE_ANALYTICS : command.purpose();
        if (!PURPOSE_ANALYTICS.equals(purpose)) {
            errors.add(new EventViolation("purpose", "UNKNOWN_PURPOSE", "Chỉ hỗ trợ mục đích analytics."));
        }
        if (command.choice() == null || !CHOICES.contains(command.choice())) {
            errors.add(new EventViolation("choice", "INVALID_CHOICE", "choice phải là granted hoặc withdrawn."));
        }
        if (command.policyVersion() == null || !POLICY_VERSION.matcher(command.policyVersion()).matches()) {
            errors.add(new EventViolation("policyVersion", "INVALID_POLICY_VERSION", "policyVersion có dạng YYYY-MM-DD."));
        }
        if (command.source() == null || !SOURCES.contains(command.source())) {
            errors.add(new EventViolation("source", "INVALID_SOURCE", "source phải là banner hoặc preferences."));
        }
        if (!errors.isEmpty()) throw new InvalidEventsException(errors);
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        records.insert(new ConsentRecordRepository.ConsentRecord(id, command.consentId(), purpose, command.choice(),
                command.policyVersion(), command.source(), userId, now));
        return new ConsentReceipt(id, now);
    }
}
