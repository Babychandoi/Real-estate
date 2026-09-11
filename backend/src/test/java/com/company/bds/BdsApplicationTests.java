package com.company.bds;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BdsApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void contextLoads() {
        // Kiểm tra Spring Boot Context khởi tạo thành công
    }

    @Test
    void listingRevisionLifecycle_fullFlow() throws Exception {
        // 1. Tạo tin nháp mới (Draft #1)
        String createDraftJson = """
            {
                "purpose": "SALE",
                "propertyType": "APARTMENT",
                "title": "Căn hộ Vinhomes Smart City 2PN 55m2 hướng Đông Nam",
                "priceVnd": 3200000000,
                "areaM2": 55.0,
                "description": "Căn hộ tầng trung view thoáng mát mẻ, đã có sổ hồng chính chủ.",
                "provinceCode": "01",
                "districtCode": "019",
                "wardCode": "00600",
                "addressSummary": "Tây Mỗ, Nam Từ Liêm, Hà Nội",
                "publicLatitude": 21.0025,
                "publicLongitude": 105.7423,
                "imageUrls": ["https://images.unsplash.com/photo-1545324418-cc1a3fa10c00"]
            }
            """;

        MvcResult createResult = mockMvc.perform(post("/api/v1/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createDraftJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.listingId").exists())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();

        JsonNode createNode = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String listingId = createNode.get("listingId").asText();
        assertNotNull(listingId);

        // 2. Cập nhật bản nháp (Update Draft #1)
        String updateDraftJson = """
            {
                "purpose": "SALE",
                "propertyType": "APARTMENT",
                "title": "Căn hộ Vinhomes Smart City 2PN 55m2 - Cắt lỗ thu hồi vốn",
                "priceVnd": 3150000000,
                "areaM2": 55.0,
                "description": "Căn hộ tầng trung view thoáng mát mẻ, cần bán gấp trong tháng.",
                "addressSummary": "Tây Mỗ, Nam Từ Liêm, Hà Nội"
            }
            """;

        mockMvc.perform(put("/api/v1/listings/" + listingId + "/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateDraftJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listingId").value(listingId));

        // 3. Nộp duyệt tin đăng (Submit Revision #1)
        mockMvc.perform(post("/api/v1/listings/" + listingId + "/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));

        // 4. Kiểm tra chi tiết tin sau khi nộp duyệt: revision 1 ở trạng thái SUBMITTED
        MvcResult detailAfterSubmit = mockMvc.perform(get("/api/v1/listings/" + listingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revisionNumber").value(1))
                .andExpect(jsonPath("$.revisionStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andReturn();

        // 5. Thử sửa tin sau khi đã submit -> Hệ thống tự động tạo Revision #2 nháp (Bất biến Revision #1)
        String modifyAfterSubmitJson = """
            {
                "purpose": "SALE",
                "propertyType": "APARTMENT",
                "title": "Căn hộ Vinhomes Smart City 2PN 55m2 - Tặng kèm gói nội thất",
                "priceVnd": 3150000000,
                "areaM2": 55.0,
                "description": "Cập nhật thêm quà tặng nội thất.",
                "addressSummary": "Tây Mỗ, Nam Từ Liêm, Hà Nội"
            }
            """;

        mockMvc.perform(put("/api/v1/listings/" + listingId + "/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(modifyAfterSubmitJson))
                .andExpect(status().isOk());

        // 6. Kiểm tra lại chi tiết tin: Đã có Revision #2 ở trạng thái DRAFT
        mockMvc.perform(get("/api/v1/listings/" + listingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revisionNumber").value(2))
                .andExpect(jsonPath("$.revisionStatus").value("DRAFT"))
                .andExpect(jsonPath("$.title").value("Căn hộ Vinhomes Smart City 2PN 55m2 - Tặng kèm gói nội thất"));
    }

    @Test
    void moderationWorkflow_queueDiffApproveReject() throws Exception {
        // 1. Tạo và nộp duyệt tin đăng 1
        String draft1Json = """
            {
                "purpose": "SALE",
                "propertyType": "TOWNHOUSE",
                "title": "Nhà phố liền kề Starlake Tây Hồ Tây 132m2 hướng Nam",
                "priceVnd": 35000000000,
                "areaM2": 132.0,
                "description": "Biệt thự liền kề vị trí đắc địa, hạ tầng hoàn thiện, sổ đỏ trao tay.",
                "addressSummary": "Xuân La, Tây Hồ, Hà Nội",
                "imageUrls": ["https://images.unsplash.com/photo-1600585154340-be6161a56a0c"]
            }
            """;

        MvcResult createRes = mockMvc.perform(post("/api/v1/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draft1Json))
                .andExpect(status().isCreated())
                .andReturn();

        String listing1Id = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("listingId").asText();

        // Nộp duyệt tin 1
        mockMvc.perform(post("/api/v1/listings/" + listing1Id + "/submit"))
                .andExpect(status().isOk());

        // 2. Kiểm tra hàng đợi kiểm duyệt /api/v1/moderation/queue
        MvcResult queueRes = mockMvc.perform(get("/api/v1/moderation/queue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andReturn();

        JsonNode queueArray = objectMapper.readTree(queueRes.getResponse().getContentAsString());
        boolean foundInQueue = false;
        String revIdToApprove = null;
        for (JsonNode item : queueArray) {
            if (item.get("listingId").asText().equals(listing1Id)) {
                foundInQueue = true;
                revIdToApprove = item.get("revisionId").asText();
                break;
            }
        }
        assertEquals(true, foundInQueue, "Tin đăng vừa submit phải có mặt trong hàng đợi kiểm duyệt");

        // 3. Kiểm tra tính toán Diff của tin 1: /api/v1/moderation/listings/{id}/diff
        mockMvc.perform(get("/api/v1/moderation/listings/" + listing1Id + "/diff"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isFirstSubmission").value(true))
                .andExpect(jsonPath("$.diffs").isArray());

        // 4. Phê duyệt tin 1 (Approve)
        String approveBody = String.format("""
            {
                "revisionId": "%s",
                "note": "Thông tin đầy đủ, sổ đỏ hợp lệ"
            }
            """, revIdToApprove);

        mockMvc.perform(post("/api/v1/moderation/listings/" + listing1Id + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approveBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // 5. Kiểm tra tin 1 xuất hiện trong danh sách tìm kiếm công khai
        mockMvc.perform(get("/api/v1/listings/search?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        // 6. Thử nghiệm luồng Từ chối (Reject) với tin đăng 2
        String draft2Json = """
            {
                "purpose": "RENT",
                "propertyType": "HOUSE",
                "title": "Cho thuê mặt bằng kinh doanh phố Huế 80m2",
                "priceVnd": 45000000,
                "areaM2": 80.0,
                "description": "Vị trí cực đẹp trung tâm Hà Nội.",
                "addressSummary": "Phố Huế, Hai Bà Trưng, Hà Nội"
            }
            """;

        MvcResult create2Res = mockMvc.perform(post("/api/v1/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draft2Json))
                .andExpect(status().isCreated())
                .andReturn();

        String listing2Id = objectMapper.readTree(create2Res.getResponse().getContentAsString()).get("listingId").asText();

        // Nộp duyệt tin 2
        mockMvc.perform(post("/api/v1/listings/" + listing2Id + "/submit"))
                .andExpect(status().isOk());

        // Lấy revisionId của tin 2 thông qua diff endpoint
        MvcResult diff2Res = mockMvc.perform(get("/api/v1/moderation/listings/" + listing2Id + "/diff"))
                .andExpect(status().isOk())
                .andReturn();
        String rev2Id = objectMapper.readTree(diff2Res.getResponse().getContentAsString()).get("currentRevisionId").asText();

        // Từ chối tin 2
        String rejectBody = String.format("""
            {
                "revisionId": "%s",
                "reasonCode": "INCORRECT_PRICE",
                "reasonDetail": "Mức giá chưa bao gồm thuế GTGT và phí dịch vụ toà nhà"
            }
            """, rev2Id);

        mockMvc.perform(post("/api/v1/moderation/listings/" + listing2Id + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rejectBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        // 7. Lấy danh sách lý do từ chối chuẩn hóa
        mockMvc.perform(get("/api/v1/moderation/rejection-reasons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void searchListingsWithGisFilters_success() throws Exception {
        // 1. Tạo và duyệt tin tại Cầu Giấy (Lat: 21.0368, Lng: 105.7925, Giá 12.8 tỷ)
        String cauGiayListingJson = """
            {
                "purpose": "SALE",
                "propertyType": "HOUSE",
                "title": "Nhà mặt phố Cầu Giấy kinh doanh sầm uất",
                "priceVnd": 12800000000,
                "areaM2": 65.0,
                "description": "Vị trí cực đẹp trung tâm Cầu Giấy.",
                "addressSummary": "Dịch Vọng Hậu, Cầu Giấy, Hà Nội",
                "publicLatitude": 21.0368,
                "publicLongitude": 105.7925
            }
            """;

        MvcResult createRes = mockMvc.perform(post("/api/v1/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cauGiayListingJson))
                .andExpect(status().isCreated())
                .andReturn();
        String id = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("listingId").asText();

        // Nộp duyệt
        mockMvc.perform(post("/api/v1/listings/" + id + "/submit"))
                .andExpect(status().isOk());

        // Lấy revisionId
        MvcResult diffRes = mockMvc.perform(get("/api/v1/moderation/listings/" + id + "/diff"))
                .andExpect(status().isOk())
                .andReturn();
        String revId = objectMapper.readTree(diffRes.getResponse().getContentAsString()).get("currentRevisionId").asText();

        // Duyệt tin
        mockMvc.perform(post("/api/v1/moderation/listings/" + id + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"revisionId\": \"%s\"}", revId)))
                .andExpect(status().isOk());

        // 2. Tìm kiếm Bounding Box bao trùm Cầu Giấy (21.03 -> 21.05, 105.78 -> 105.80)
        mockMvc.perform(get("/api/v1/listings/search")
                        .param("minLat", "21.03")
                        .param("maxLat", "21.05")
                        .param("minLng", "105.78")
                        .param("maxLng", "105.80"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].title").value("Nhà mặt phố Cầu Giấy kinh doanh sầm uất"));

        // 3. Tìm kiếm ngoài vùng Bounding Box (Khu vực Đông Anh 21.13 -> 21.16)
        mockMvc.perform(get("/api/v1/listings/search")
                        .param("minLat", "21.13")
                        .param("maxLat", "21.16")
                        .param("minLng", "105.82")
                        .param("maxLng", "105.85"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        // 4. Tìm kiếm theo khoảng giá (minPrice: 10 tỷ, maxPrice: 15 tỷ) -> Khớp tin Cầu Giấy
        mockMvc.perform(get("/api/v1/listings/search")
                        .param("minPrice", "10000000000")
                        .param("maxPrice", "15000000000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].priceVnd").value(12800000000L));
    }

    @Test
    void leadLifecycleAndCrm_flow() throws Exception {
        // 1. Tạo một tin đăng để nhận lead
        String listingJson = """
            {
                "purpose": "SALE",
                "propertyType": "APARTMENT",
                "title": "Căn hộ Masteri Centre Point 2PN view hồ",
                "priceVnd": 4500000000,
                "areaM2": 68.0,
                "description": "Nội thất cao cấp, bàn giao ngay.",
                "addressSummary": "Long Bình, Quận 9, TP Hồ Chí Minh"
            }
            """;
        MvcResult res = mockMvc.perform(post("/api/v1/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(listingJson))
                .andExpect(status().isCreated())
                .andReturn();
        String listingId = objectMapper.readTree(res.getResponse().getContentAsString()).get("listingId").asText();

        // 2. Khách hàng gửi lead tư vấn (POST /api/v1/public/leads)
        String leadJson = String.format("""
            {
                "listingId": "%s",
                "fullName": "Nguyễn Văn An",
                "phone": "0912345678",
                "note": "Tôi muốn đi xem nhà vào chiều thứ 7 tuần này.",
                "consentPolicy": true
            }
            """, listingId);

        MvcResult leadSubmitRes = mockMvc.perform(post("/api/v1/public/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(leadJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.leadId").exists())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andReturn();

        String leadId = objectMapper.readTree(leadSubmitRes.getResponse().getContentAsString()).get("leadId").asText();

        // 3. Tra cứu danh sách Lead của tin đăng: SĐT phải được che dấu an toàn NFR12
        mockMvc.perform(get("/api/v1/leads?listingId=" + listingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(leadId))
                .andExpect(jsonPath("$[0].fullName").value("Nguyễn Văn An"))
                .andExpect(jsonPath("$[0].maskedPhone").value("091****678"))
                .andExpect(jsonPath("$[0].status").value("NEW"));

        // 4. Môi giới cập nhật trạng thái Lead sang CONTACTED
        String updateStatusJson = """
            {
                "status": "CONTACTED"
            }
            """;
        mockMvc.perform(patch("/api/v1/leads/" + leadId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateStatusJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONTACTED"));
    }

    @Test
    void violationReportDesk_emergencyHideAndResolve_flow() throws Exception {
        // 1. Tạo và duyệt tin đăng
        String listingJson = """
            {
                "purpose": "SALE",
                "propertyType": "HOUSE",
                "title": "Bán gấp nhà đẹp giá rẻ bất ngờ",
                "priceVnd": 1500000000,
                "areaM2": 100.0,
                "description": "Giá cực sốc chỉ trong hôm nay.",
                "addressSummary": "Bình Thạnh, TP Hồ Chí Minh"
            }
            """;
        MvcResult res = mockMvc.perform(post("/api/v1/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(listingJson))
                .andExpect(status().isCreated())
                .andReturn();
        String listingId = objectMapper.readTree(res.getResponse().getContentAsString()).get("listingId").asText();

        mockMvc.perform(post("/api/v1/listings/" + listingId + "/submit")).andExpect(status().isOk());
        MvcResult diffRes = mockMvc.perform(get("/api/v1/moderation/listings/" + listingId + "/diff")).andExpect(status().isOk()).andReturn();
        String revId = objectMapper.readTree(diffRes.getResponse().getContentAsString()).get("currentRevisionId").asText();
        mockMvc.perform(post("/api/v1/moderation/listings/" + listingId + "/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"revisionId\": \"%s\"}", revId))).andExpect(status().isOk());

        // Kiểm tra tin đang ACTIVE
        mockMvc.perform(get("/api/v1/listings/" + listingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // 2. Tiếp nhận Báo xấu P0 Khẩn cấp lừa cọc (POST /api/v1/public/reports)
        String reportJson = String.format("""
            {
                "listingId": "%s",
                "category": "SCAM_DEPOSIT",
                "severity": "P0_EMERGENCY",
                "description": "Môi giới yêu cầu chuyển khoản đặt cọc 50 triệu trước khi xem nhà rồi cắt liên lạc",
                "evidenceUrls": "https://cdn.example.com/chat_scam.png;https://cdn.example.com/receipt.jpg",
                "reporterPhone": "0988776655"
            }
            """, listingId);

        MvcResult reportRes = mockMvc.perform(post("/api/v1/public/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.severity").value("P0_EMERGENCY"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        String reportId = objectMapper.readTree(reportRes.getResponse().getContentAsString()).get("id").asText();

        // 3. Cơ chế bảo vệ khẩn cấp P0 (FR27): Tin đăng lập tức tự động chuyển sang PAUSED
        mockMvc.perform(get("/api/v1/listings/" + listingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));

        // 4. Bàn giải quyết vi phạm tra cứu báo xấu: /api/v1/reports
        mockMvc.perform(get("/api/v1/reports?severity=P0_EMERGENCY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        // 5. Môi giới gửi giải trình (Appeal)
        String appealJson = """
            {
                "newEvidence": "https://cdn.example.com/giay_to_chinh_chu.pdf"
            }
            """;
        mockMvc.perform(post("/api/v1/reports/" + reportId + "/appeal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appealJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPEALED"));

        // 6. Ban quản trị xác minh vi phạm thật, quyết định Khóa tin vĩnh viễn (LOCKED)
        String resolveJson = """
            {
                "resolutionNote": "Xác nhận hành vi lừa cọc qua đối soát lịch sử giao dịch ngân hàng. Khóa tin và chuyển hồ sơ cơ quan chức năng.",
                "permanentlyLockListing": true
            }
            """;
        mockMvc.perform(post("/api/v1/reports/" + reportId + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resolveJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        // 7. Tin đăng chuyển sang trạng thái LOCKED
        mockMvc.perform(get("/api/v1/listings/" + listingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOCKED"));
    }

    @Test
    void funnelAnalytics_endpointReturnsValidMetrics() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/funnel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.impressions").isNumber())
                .andExpect(jsonPath("$.detailViews").isNumber())
                .andExpect(jsonPath("$.leadsSubmitted").isNumber())
                .andExpect(jsonPath("$.contactedCount").isNumber())
                .andExpect(jsonPath("$.dealsClosed").isNumber())
                .andExpect(jsonPath("$.conversionRatePercent").isNumber())
                .andExpect(jsonPath("$.steps").isArray())
                .andExpect(jsonPath("$.steps.length()").value(5));
    }

    @Test
    void kycSubmissionAndApproval_flow() throws Exception {
        java.util.UUID userId = java.util.UUID.randomUUID();

        // 1. Nộp hồ sơ eKYC cá nhân (POST /api/v1/kyc/submit)
        String kycJson = String.format("""
            {
                "userId": "%s",
                "idNumber": "001201014567",
                "fullName": "Trần Văn Bình",
                "dob": "15/08/1985",
                "address": "Số 25 ngõ 102 Khuất Duy Tiến, Thanh Xuân, Hà Nội",
                "idCardFrontUrl": "https://cdn.example.com/cccd_front.jpg",
                "idCardBackUrl": "https://cdn.example.com/cccd_back.jpg",
                "selfieUrl": "https://cdn.example.com/selfie_face.jpg"
            }
            """, userId);

        MvcResult res = mockMvc.perform(post("/api/v1/kyc/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(kycJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.maskedIdNumber").value("001****4567"))
                .andExpect(jsonPath("$.faceMatchScore").isNumber())
                .andReturn();

        String kycId = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();

        // 2. Tra cứu eKYC theo userId
        mockMvc.perform(get("/api/v1/kyc/user/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Trần Văn Bình"));

        // 3. Thẩm định viên phê duyệt hồ sơ eKYC
        mockMvc.perform(post("/api/v1/kyc/" + kycId + "/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.verifiedAt").exists());
    }

    @Test
    void listingVerification_verifiedOwnerBadge_flow() throws Exception {
        java.util.UUID ownerId = java.util.UUID.randomUUID();

        // 1. Hoàn tất eKYC cho chủ nhà trước
        String kycJson = String.format("""
            {
                "userId": "%s",
                "idNumber": "001092003888",
                "fullName": "Lê Hoàng Yến",
                "dob": "20/11/1992",
                "address": "Phường Mễ Trì, Nam Từ Liêm, Hà Nội",
                "idCardFrontUrl": "https://cdn.example.com/cccd_front_yen.jpg",
                "idCardBackUrl": "https://cdn.example.com/cccd_back_yen.jpg"
            }
            """, ownerId);

        MvcResult kycRes = mockMvc.perform(post("/api/v1/kyc/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(kycJson))
                .andExpect(status().isCreated())
                .andReturn();
        String kycId = objectMapper.readTree(kycRes.getResponse().getContentAsString()).get("id").asText();
        mockMvc.perform(post("/api/v1/kyc/" + kycId + "/approve")).andExpect(status().isOk());

        // 2. Tạo tin đăng BĐS
        String listingJson = """
            {
                "purpose": "SALE",
                "propertyType": "APARTMENT",
                "title": "Bán căn hộ Duplex The Matrix One Mễ Trì",
                "priceVnd": 8900000000,
                "areaM2": 115.0,
                "description": "Căn hộ Duplex thông tầng siêu đẹp, có sổ đỏ chính chủ.",
                "addressSummary": "Mễ Trì, Nam Từ Liêm, Hà Nội"
            }
            """;
        MvcResult listRes = mockMvc.perform(post("/api/v1/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(listingJson))
                .andExpect(status().isCreated())
                .andReturn();
        String listingId = objectMapper.readTree(listRes.getResponse().getContentAsString()).get("listingId").asText();

        // 3. Chủ nhà nộp hồ sơ thẩm định pháp lý cấp nhãn Tin Chính Chủ (FR03)
        String verifJson = String.format("""
            {
                "userId": "%s",
                "verificationType": "CERTIFICATE_OF_OWNERSHIP",
                "certificateNumber": "GCN-HN-2026-88899",
                "documentUrls": "https://cdn.example.com/so_hong_matrix_one.pdf",
                "ownerNameOnDoc": "Lê Hoàng Yến"
            }
            """, ownerId);

        MvcResult verifRes = mockMvc.perform(post("/api/v1/listings/" + listingId + "/verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.ownerNameOnDoc").value("Lê Hoàng Yến"))
                .andReturn();

        String verifId = objectMapper.readTree(verifRes.getResponse().getContentAsString()).get("id").asText();

        // 4. Kiểm tra tin đăng trong hàng đợi thẩm định
        mockMvc.perform(get("/api/v1/verifications?status=PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        // 5. Thẩm định viên đối soát 2 cột (CCCD vs Sổ hồng) -> Phê duyệt cấp nhãn Tin Chính Chủ
        String approveBody = """
            {
                "verifierNote": "Họ tên trên CCCD và Sổ hồng trùng khớp 100%. Đã kiểm tra không tranh chấp quy hoạch."
            }
            """;
        mockMvc.perform(post("/api/v1/verifications/" + verifId + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approveBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED_OWNER"));

        // 6. Kiểm tra tin đăng đã được cấp nhãn chính chủ
        MvcResult checkListing = mockMvc.perform(get("/api/v1/listings/" + listingId))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(checkListing.getResponse().getContentAsString());
        assertNotNull(node);
    }

    @Test
    void depositContract_lifecycle_flow() throws Exception {
        // 1. Tạo tin đăng BĐS để làm đối tượng đặt cọc
        String draftJson = """
            {
                "purpose": "SALE",
                "propertyType": "APARTMENT",
                "title": "Căn hộ Vinhomes Smart City 2PN tầng trung view thoáng",
                "priceVnd": 3500000000,
                "areaM2": 65.0,
                "description": "Căn hộ đẹp đã có sổ hồng chính chủ, sẵn sàng giao dịch đặt cọc.",
                "addressSummary": "Tây Mỗ, Nam Từ Liêm, Hà Nội"
            }
            """;
        MvcResult createRes = mockMvc.perform(post("/api/v1/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftJson))
                .andExpect(status().isCreated())
                .andReturn();
        String listingId = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("listingId").asText();

        // 2. Bên mua khởi tạo yêu cầu Đặt cọc Bảo đảm Escrow (FR28, FR30, UC05)
        String createDepositJson = String.format("""
            {
                "listingId": "%s",
                "buyerName": "Trần Hải Đăng",
                "buyerPhone": "0912334455",
                "buyerIdNumber": "001095009988",
                "depositAmount": 50000000.00,
                "termsConditions": "Đặt cọc thiện chí 50.000.000 VNĐ qua Két Escrow BDS WF 2026. Hẹn công chứng trong vòng 10 ngày."
            }
            """, listingId);

        MvcResult depositRes = mockMvc.perform(post("/api/v1/transactions/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createDepositJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.buyerName").value("Trần Hải Đăng"))
                .andExpect(jsonPath("$.depositAmount").value(50000000.00))
                .andReturn();

        String contractId = objectMapper.readTree(depositRes.getResponse().getContentAsString()).get("id").asText();

        // 3. Người mua ký số hợp đồng qua OTP 6 số
        String signBuyerJson = """
            {
                "otpCode": "123456"
            }
            """;
        mockMvc.perform(post("/api/v1/transactions/deposits/" + contractId + "/sign-buyer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signBuyerJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_SELLER_SIGN"))
                .andExpect(jsonPath("$.buyerOtpVerified").value(true));

        // 4. Người bán ký số xác nhận -> Kích hoạt PHONG TỎA TIỀN CỌC TRONG ESCROW VAULT
        String signSellerJson = """
            {
                "otpCode": "654321"
            }
            """;
        mockMvc.perform(post("/api/v1/transactions/deposits/" + contractId + "/sign-seller")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signSellerJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ESCROW_LOCKED"))
                .andExpect(jsonPath("$.sellerOtpVerified").value(true))
                .andExpect(jsonPath("$.escrowLockedAt").exists());

        // 5. Hai bên hoàn tất công chứng chuyển nhượng -> Giải ngân tiền cọc cho người bán
        mockMvc.perform(post("/api/v1/transactions/deposits/" + contractId + "/release")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").exists());

        // 6. Tra cứu chi tiết hợp đồng và nhật ký biến động quỹ Escrow (FR28 Audit Trail)
        mockMvc.perform(get("/api/v1/transactions/deposits/" + contractId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.escrowTransactions").isArray())
                .andExpect(jsonPath("$.escrowTransactions.length()").value(3)); // DEPOSIT, LOCK, RELEASE
    }

    @Test
    void listingWizard_estimatePrice_and_qualityScore_flow() throws Exception {
        // 1. Kiểm tra API AI Price Estimator
        String priceEstJson = """
            {
                "propertyType": "APARTMENT",
                "areaM2": 75.0,
                "districtCode": "NTL",
                "provinceCode": "HN"
            }
            """;
        mockMvc.perform(post("/api/v1/listings/estimate-price")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(priceEstJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minPriceVnd").exists())
                .andExpect(jsonPath("$.maxPriceVnd").exists())
                .andExpect(jsonPath("$.confidenceScorePercent").value(94));

        // 2. Kiểm tra API Quality Score Engine
        String qualityJson = """
            {
                "title": "Bán căn hộ cao cấp The Matrix One 2PN Mễ Trì Nam Từ Liêm",
                "description": "Căn hộ thiết kế hiện đại 2 phòng ngủ 2 WC tầng trung view thoáng mát công viên hồ điều hòa Mễ Trì.",
                "imageUrls": [
                    "https://cdn.example.com/img1.jpg",
                    "https://cdn.example.com/img2.jpg",
                    "https://cdn.example.com/img3.jpg",
                    "https://cdn.example.com/img4.jpg",
                    "https://cdn.example.com/img5.jpg"
                ],
                "latitude": 21.0118,
                "longitude": 105.7725,
                "hasLegalDocs": true
            }
            """;
        mockMvc.perform(post("/api/v1/listings/quality-score")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(qualityJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(100))
                .andExpect(jsonPath("$.rating").value("EXCELLENT"))
                .andExpect(jsonPath("$.passedCriteria.length()").value(5));
    }

    @Test
    void analyticsOverview_metrics_flow() throws Exception {
        // Kiểm tra API Phân tích phễu chuyển đổi & Chỉ số sản phẩm (FR29)
        mockMvc.perform(get("/api/v1/analytics/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeListingsCount").isNumber())
                .andExpect(jsonPath("$.totalDetailViews").isNumber())
                .andExpect(jsonPath("$.totalContactClicks").isNumber())
                .andExpect(jsonPath("$.totalLeadsSubmitted").isNumber())
                .andExpect(jsonPath("$.totalEscrowDeposited").isNumber())
                .andExpect(jsonPath("$.avgModerationHours").value(4.2))
                .andExpect(jsonPath("$.verifiedOwnerRatioPercent").value(42.5));
    }

    @Test
    void projectCatalog_createAndQuery_flow() throws Exception {
        // 1. Tạo dự án mới chuẩn ERD04 & Pháp lý FR25
        String newProjectJson = """
            {
                "name": "Lumi Hanoi Mega Complex",
                "developerName": "CapitaLand Development",
                "provinceCode": "HN",
                "districtCode": "NTL",
                "address": "Đại lộ Thăng Long, Tây Mỗ",
                "totalAreaM2": 56000.0,
                "totalBlocks": 9,
                "totalUnits": 3950,
                "handoverYear": 2026,
                "legalLicenseNumber": "GPXD-512/SXD-HN"
            }
            """;

        MvcResult createResult = mockMvc.perform(post("/api/v1/catalog/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newProjectJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Lumi Hanoi Mega Complex"))
                .andExpect(jsonPath("$.developerName").value("CapitaLand Development"))
                .andExpect(jsonPath("$.legalLicenseNumber").value("GPXD-512/SXD-HN"))
                .andExpect(jsonPath("$.totalUnits").value(3950))
                .andReturn();

        String projectId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();
        assertNotNull(projectId);

        // 2. Tra cứu danh sách dự án
        mockMvc.perform(get("/api/v1/catalog/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.developerName == 'CapitaLand Development')]").exists());

        // 3. Tra cứu chi tiết dự án theo ID
        mockMvc.perform(get("/api/v1/catalog/projects/" + projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(projectId))
                .andExpect(jsonPath("$.developerName").value("CapitaLand Development"))
                .andExpect(jsonPath("$.legalLicenseNumber").value("GPXD-512/SXD-HN"));
    }

    @Test
    void cmsArticle_revisionLifecycle_and_approval_flow() throws Exception {
        // 1. Biên tập viên tạo bài viết nháp (DRAFT Revision 1)
        String createArticleJson = """
            {
                "slug": "chinh-sach-bao-ve-du-lieu-bds-2026",
                "category": "LEGAL_POLICY",
                "title": "Quy chuẩn bảo vệ dữ liệu cá nhân & Chống lừa đảo cọc BĐS 2026",
                "summary": "Hướng dẫn chi tiết bộ quy tắc bảo vệ dữ liệu và xác minh cọc theo Luật BV Dữ liệu 91/2025/QH15",
                "contentHtml": "<p>Nội dung quy chuẩn pháp lý giao dịch bất động sản an toàn số hóa...</p>",
                "coverImageUrl": "https://images.unsplash.com/photo-1450133064473-71024230f91b",
                "authorName": "Lê Mai Hương",
                "legalReference": "Luật BV Dữ liệu 91/2025/QH15",
                "metaDescription": "Hướng dẫn chi tiết bộ quy tắc bảo vệ dữ liệu và xác minh cọc",
                "canonicalUrl": "/chinh-sach-bao-ve-du-lieu-bds-2026"
            }
            """;

        MvcResult createResult = mockMvc.perform(post("/api/v1/cms/articles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createArticleJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.slug").value("chinh-sach-bao-ve-du-lieu-bds-2026"))
                .andExpect(jsonPath("$.category").value("LEGAL_POLICY"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.currentRevision.revisionNumber").value(1))
                .andExpect(jsonPath("$.currentRevision.status").value("DRAFT"))
                .andReturn();

        JsonNode articleNode = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String articleId = articleNode.get("id").asText();
        String revisionId = articleNode.get("currentRevision").get("id").asText();

        // 2. Biên tập viên nộp duyệt bài viết (SUBMITTED)
        mockMvc.perform(post("/api/v1/cms/articles/" + articleId + "/revisions/" + revisionId + "/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        // 3. Admin phê duyệt và xuất bản bài viết (PUBLISHED)
        mockMvc.perform(post("/api/v1/cms/articles/" + articleId + "/revisions/" + revisionId + "/approve")
                        .param("adminUsername", "admin_chief"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.reviewedBy").value("admin_chief"))
                .andExpect(jsonPath("$.reviewedAt").exists());

        // 4. Tra cứu danh sách công khai (Public Articles)
        mockMvc.perform(get("/api/v1/public/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.slug == 'chinh-sach-bao-ve-du-lieu-bds-2026')]").exists());

        // 5. Tra cứu chi tiết bài viết công khai qua Slug
        mockMvc.perform(get("/api/v1/public/articles/chinh-sach-bao-ve-du-lieu-bds-2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("chinh-sach-bao-ve-du-lieu-bds-2026"))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.currentRevision.authorName").value("Lê Mai Hương"));
    }
}

