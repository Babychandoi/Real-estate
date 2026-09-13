package com.company.bds.shared.outbox;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.outbox.enabled", havingValue = "true")
public class OutboxWebhookDispatcher {
    private final OutboxLeaseRepository leases;
    private final RestClient client;
    private final String webhookUrl;
    private final byte[] signingKey;
    private final String workerId = UUID.randomUUID().toString();

    public OutboxWebhookDispatcher(OutboxLeaseRepository leases, RestClient.Builder builder,
            @Value("${app.outbox.webhook-url}") String webhookUrl,
            @Value("${app.outbox.signing-key}") String signingKey) {
        this.leases = leases;
        this.client = builder.build();
        this.webhookUrl = webhookUrl;
        this.signingKey = signingKey.getBytes(StandardCharsets.UTF_8);
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-delay-ms:1000}")
    public void dispatch() {
        for (OutboxLeaseRepository.ClaimedEvent event : leases.claim(workerId, 20)) {
            try {
                client.post().uri(webhookUrl).contentType(MediaType.APPLICATION_JSON)
                        .header("X-Event-Id", event.id().toString())
                        .header("X-Event-Type", event.eventType())
                        .header("X-Event-Signature", signature(event.id() + "." + event.payload()))
                        .body(event.payload()).retrieve().toBodilessEntity();
                if (!leases.complete(event.id(), event.leaseToken())) {
                    throw new IllegalStateException("Outbox lease lost before finalize");
                }
            } catch (Exception ex) {
                leases.fail(event.id(), event.leaseToken(), ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
            }
        }
    }

    private String signature(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot sign outbox webhook", ex);
        }
    }
}

