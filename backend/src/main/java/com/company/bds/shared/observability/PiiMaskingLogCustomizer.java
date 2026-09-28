package com.company.bds.shared.observability;

import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

/**
 * Plugs {@link PiiLogMasker} into Spring Boot structured (JSON) logging: every string value of every log line is
 * masked before it is written. Registered with {@code logging.structured.json.customizer} in {@code application.yml}.
 */
public class PiiMaskingLogCustomizer implements StructuredLoggingJsonMembersCustomizer<Object> {

    @Override
    public void customize(JsonWriter.Members<Object> members) {
        members.applyingValueProcessor(JsonWriter.ValueProcessor.of(String.class, PiiLogMasker::mask));
    }
}
