package com.company.bds.lead.application;

import com.company.bds.lead.domain.model.Lead;
import com.company.bds.lead.domain.model.LeadStatus;
import com.company.bds.lead.domain.port.LeadPersistencePort;
import com.company.bds.listing.application.port.out.ListingPersistencePort;
import com.company.bds.listing.domain.model.Listing;
import com.company.bds.listing.domain.model.ListingStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.company.bds.shared.security.PiiProtectionService;
import com.company.bds.iam.application.AuthService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.ObjectProvider;
import com.company.bds.shared.outbox.OutboxEventWriter;

import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class LeadApplicationService {

    private final LeadPersistencePort leadPersistencePort;
    private final ListingPersistencePort listingPersistencePort;
    private final PiiProtectionService piiProtection;
    private final JdbcTemplate jdbc;
    private final ObjectProvider<OutboxEventWriter> outbox;

    public LeadApplicationService(
            LeadPersistencePort leadPersistencePort,
            ListingPersistencePort listingPersistencePort,
            PiiProtectionService piiProtection,
            JdbcTemplate jdbc,
            ObjectProvider<OutboxEventWriter> outbox) {
        this.leadPersistencePort = leadPersistencePort;
        this.listingPersistencePort = listingPersistencePort;
        this.piiProtection = piiProtection;
        this.jdbc = jdbc;
        this.outbox = outbox;
    }

    /**
     * Khách hàng gửi biểu mẫu liên hệ / Hộp tiếp nhận Lead.
     * Mã hóa SĐT và băm tra cứu theo NFR12, kiểm soát spam.
     */
    public Lead submitLead(
            UUID listingId,
            String fullName,
            String rawPhone,
            String note,
            boolean consentPolicy,
            String idempotencyKey) {

        // 1. Kiểm tra tin đăng có tồn tại không
        Listing listing = listingPersistencePort.findById(listingId)
                .orElseThrow(() -> new IllegalArgumentException("Tin đăng không tồn tại ID: " + listingId));
        if (listing.getStatus() != ListingStatus.ACTIVE || listing.getPublicRevisionId() == null) {
            throw new IllegalStateException("Tin đăng không còn nhận yêu cầu liên hệ.");
        }
        Lead replay = findIdempotentReplay(listingId, fullName, rawPhone, note, consentPolicy, idempotencyKey);
        if (replay != null) return replay;

        // 2. Chống spam: băm SĐT tra cứu tần suất gửi
        String lookupHash = piiProtection.blindIndex(rawPhone);
        long existingCount = leadPersistencePort.countByPhoneLookupHashSince(lookupHash, Instant.now().minusSeconds(24 * 60 * 60));
        if (existingCount >= 10) {
            // Đánh dấu hoặc giới hạn tần suất nếu vượt ngưỡng (NFR12)
            throw new IllegalStateException("Số điện thoại này đã gửi quá nhiều yêu cầu trong thời gian ngắn.");
        }

        // 3. Khởi tạo và lưu Lead
        UUID leadId = UUID.randomUUID();
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            if (!idempotencyKey.matches("[A-Za-z0-9._:-]{8,128}")) {
                throw new IllegalArgumentException("Idempotency-Key không hợp lệ.");
            }
            String requestHash = AuthService.sha256(listingId + "|" + fullName.trim() + "|" + rawPhone.trim()
                    + "|" + Objects.toString(note, "") + "|" + consentPolicy);
            int inserted = jdbc.update("""
                    MERGE INTO api_idempotency_keys target
                    USING (VALUES ('PUBLIC_LEAD', CAST(? AS VARCHAR(128)), CAST(? AS VARCHAR(64)), CAST(? AS UUID)))
                          source(scope,idempotency_key,request_hash,resource_id)
                    ON target.scope=source.scope AND target.idempotency_key=source.idempotency_key
                    WHEN NOT MATCHED THEN INSERT (scope,idempotency_key,request_hash,resource_id)
                    VALUES (source.scope,source.idempotency_key,source.request_hash,source.resource_id)
                    """, idempotencyKey, requestHash, leadId);
            if (inserted == 0) {
                Map<String, Object> existing = jdbc.queryForMap("""
                        SELECT request_hash,resource_id FROM api_idempotency_keys
                        WHERE scope='PUBLIC_LEAD' AND idempotency_key=?
                        """, idempotencyKey);
                if (!requestHash.equals(existing.get("request_hash"))) {
                    throw new IllegalStateException("Idempotency-Key đã được dùng cho yêu cầu khác.");
                }
                return leadPersistencePort.findById((UUID) existing.get("resource_id"))
                        .orElseThrow(() -> new IllegalStateException("Yêu cầu đang được xử lý; vui lòng thử lại."));
            }
        }
        PiiProtectionService.ProtectedValue protectedPhone = piiProtection.protect(rawPhone);
        Lead lead = new Lead(leadId, listingId, fullName.trim(), protectedPhone.encrypted(),
                protectedPhone.blindIndex(), note, consentPolicy, LeadStatus.NEW, Instant.now());

        Lead saved = leadPersistencePort.save(lead);
        OutboxEventWriter writer = outbox.getIfAvailable();
        if (writer != null) {
            writer.append("LEAD", saved.getId(), "LEAD_CREATED", Map.of(
                    "leadId", saved.getId(), "listingId", saved.getListingId(), "createdAt", saved.getCreatedAt()));
        }
        return saved;
    }

    /**
     * Lấy danh sách Lead thuộc về các tin đăng của một Môi giới (Broker Network).
     */
    @Transactional(readOnly = true)
    public List<Lead> getLeadsForBroker(UUID brokerId, int page, int size) {
        List<Listing> brokerListings = listingPersistencePort.findByOwnerId(brokerId);
        if (brokerListings.isEmpty()) {
            return List.of();
        }
        List<UUID> listingIds = brokerListings.stream().map(Listing::getId).toList();
        return leadPersistencePort.findByListingIds(listingIds, page, size);
    }

    /**
     * Lấy toàn bộ Lead cho Bàn điều phối / Quản trị viên.
     */
    @Transactional(readOnly = true)
    public List<Lead> getAllLeads(int page, int size) {
        return leadPersistencePort.findPage(page, size);
    }

    /**
     * Lấy danh sách Lead theo tin đăng cụ thể.
     */
    @Transactional(readOnly = true)
    public List<Lead> getLeadsByListing(UUID listingId, UUID actorId, boolean privileged, int page, int size) {
        Listing listing = listingPersistencePort.findById(listingId)
                .orElseThrow(() -> new IllegalArgumentException("Tin đăng không tồn tại."));
        if (!privileged && !listing.getOwnerId().equals(actorId)) {
            throw new org.springframework.security.access.AccessDeniedException("Không có quyền xem lead của tin đăng này.");
        }
        return leadPersistencePort.findByListingId(listingId, page, size);
    }

    /**
     * Môi giới hoặc Admin cập nhật tiến trình chăm sóc Lead (NEW -> CONTACTED -> APPOINTED -> CLOSED -> SPAM).
     */
    public Lead updateLeadStatus(UUID leadId, LeadStatus newStatus, UUID actorId, boolean privileged) {
        Lead lead = leadPersistencePort.findById(leadId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy Lead ID: " + leadId));
        Listing listing = listingPersistencePort.findById(lead.getListingId())
                .orElseThrow(() -> new IllegalArgumentException("Tin đăng không tồn tại."));
        if (!privileged && !listing.getOwnerId().equals(actorId)) {
            throw new org.springframework.security.access.AccessDeniedException("Không có quyền cập nhật lead này.");
        }
        lead.updateStatus(newStatus);
        return leadPersistencePort.save(lead);
    }

    @Transactional(readOnly = true)
    public String revealPhone(UUID leadId, UUID actorId, boolean privileged) {
        Lead lead = leadPersistencePort.findById(leadId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lead."));
        Listing listing = listingPersistencePort.findById(lead.getListingId())
                .orElseThrow(() -> new IllegalArgumentException("Tin đăng không tồn tại."));
        if (!privileged && !listing.getOwnerId().equals(actorId)) {
            throw new org.springframework.security.access.AccessDeniedException("Không có quyền xem liên hệ của lead này.");
        }
        if (!lead.isConsentPolicy()) throw new IllegalStateException("Khách chưa đồng ý chia sẻ thông tin liên hệ.");
        return piiProtection.reveal(lead.getPhoneEncrypted());
    }

    private Lead findIdempotentReplay(UUID listingId, String fullName, String rawPhone, String note,
            boolean consentPolicy, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return null;
        if (!idempotencyKey.matches("[A-Za-z0-9._:-]{8,128}")) {
            throw new IllegalArgumentException("Idempotency-Key không hợp lệ.");
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT request_hash,resource_id FROM api_idempotency_keys
                WHERE scope='PUBLIC_LEAD' AND idempotency_key=?
                """, idempotencyKey);
        if (rows.isEmpty()) return null;
        String requestHash = AuthService.sha256(listingId + "|" + fullName.trim() + "|" + rawPhone.trim()
                + "|" + Objects.toString(note, "") + "|" + consentPolicy);
        if (!requestHash.equals(rows.get(0).get("request_hash"))) {
            throw new IllegalStateException("Idempotency-Key đã được dùng cho yêu cầu khác.");
        }
        return leadPersistencePort.findById((UUID) rows.get(0).get("resource_id"))
                .orElseThrow(() -> new IllegalStateException("Yêu cầu đang được xử lý; vui lòng thử lại."));
    }
}
