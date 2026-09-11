package com.company.bds.lead.application;

import com.company.bds.lead.domain.model.Lead;
import com.company.bds.lead.domain.model.LeadStatus;
import com.company.bds.lead.domain.port.LeadPersistencePort;
import com.company.bds.listing.application.port.out.ListingPersistencePort;
import com.company.bds.listing.domain.model.Listing;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class LeadApplicationService {

    private final LeadPersistencePort leadPersistencePort;
    private final ListingPersistencePort listingPersistencePort;

    public LeadApplicationService(
            LeadPersistencePort leadPersistencePort,
            ListingPersistencePort listingPersistencePort) {
        this.leadPersistencePort = leadPersistencePort;
        this.listingPersistencePort = listingPersistencePort;
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
            boolean consentPolicy) {

        // 1. Kiểm tra tin đăng có tồn tại không
        listingPersistencePort.findById(listingId)
                .orElseThrow(() -> new IllegalArgumentException("Tin đăng không tồn tại ID: " + listingId));

        // 2. Chống spam: băm SĐT tra cứu tần suất gửi
        String lookupHash = Integer.toHexString(rawPhone.trim().hashCode());
        long existingCount = leadPersistencePort.countByPhoneLookupHash(lookupHash);
        if (existingCount >= 10) {
            // Đánh dấu hoặc giới hạn tần suất nếu vượt ngưỡng (NFR12)
            throw new IllegalStateException("Số điện thoại này đã gửi quá nhiều yêu cầu trong thời gian ngắn.");
        }

        // 3. Khởi tạo và lưu Lead
        Lead lead = Lead.create(
                listingId,
                fullName.trim(),
                rawPhone.trim(),
                note,
                consentPolicy,
                Instant.now()
        );

        return leadPersistencePort.save(lead);
    }

    /**
     * Lấy danh sách Lead thuộc về các tin đăng của một Môi giới (Broker Network).
     */
    @Transactional(readOnly = true)
    public List<Lead> getLeadsForBroker(UUID brokerId) {
        List<Listing> brokerListings = listingPersistencePort.findByOwnerId(brokerId);
        if (brokerListings.isEmpty()) {
            return List.of();
        }
        List<UUID> listingIds = brokerListings.stream().map(Listing::getId).toList();
        return leadPersistencePort.findByListingIds(listingIds);
    }

    /**
     * Lấy toàn bộ Lead cho Bàn điều phối / Quản trị viên.
     */
    @Transactional(readOnly = true)
    public List<Lead> getAllLeads() {
        return leadPersistencePort.findAll();
    }

    /**
     * Lấy danh sách Lead theo tin đăng cụ thể.
     */
    @Transactional(readOnly = true)
    public List<Lead> getLeadsByListing(UUID listingId) {
        return leadPersistencePort.findByListingId(listingId);
    }

    /**
     * Môi giới hoặc Admin cập nhật tiến trình chăm sóc Lead (NEW -> CONTACTED -> APPOINTED -> CLOSED -> SPAM).
     */
    public Lead updateLeadStatus(UUID leadId, LeadStatus newStatus) {
        Lead lead = leadPersistencePort.findById(leadId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy Lead ID: " + leadId));
        lead.updateStatus(newStatus);
        return leadPersistencePort.save(lead);
    }
}
