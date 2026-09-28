package com.company.bds.shared.mail;

import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import org.springframework.stereotype.Component;

/**
 * The single rule for "is this a usable recipient address": exactly what the API's {@code @Email} accepts (Hibernate
 * Validator), plus no line breaks and at most 254 characters. Used by {@link MailOutbox} and by every place that stores an
 * address for later mail (e.g. the billing admin notification address), so an address accepted when it is saved is never
 * rejected when a message is queued.
 */
@Component
public class MailAddressValidator {
    static final int MAX_LENGTH = 254;

    private final Validator validator;

    public MailAddressValidator(Validator validator) {
        this.validator = validator;
    }

    public boolean isValid(String address) {
        if (address == null || address.isBlank() || address.length() > MAX_LENGTH) return false;
        if (address.indexOf('\r') >= 0 || address.indexOf('\n') >= 0 || !address.equals(address.trim())) return false;
        return validator.validateValue(Candidate.class, "address", address).isEmpty();
    }

    /** Carrier for the {@code @Email} constraint (the same annotation the request DTOs use). */
    static final class Candidate {
        @Email
        String address;
    }
}
