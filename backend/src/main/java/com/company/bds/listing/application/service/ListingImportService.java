package com.company.bds.listing.application.service;

import com.company.bds.listing.application.command.CreateListingDraftCommand;
import com.company.bds.listing.domain.exception.ListingValidationException;
import com.company.bds.listing.domain.model.Furnishing;
import com.company.bds.listing.domain.model.LegalStatusCode;
import com.company.bds.listing.domain.model.ListingAttributes;
import com.company.bds.listing.domain.model.ListingPurpose;
import com.company.bds.listing.domain.model.PropertyType;
import com.company.bds.shared.security.ContactInfoGuard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * CSV import of listing drafts (P-08). Dry run validates every row (types, ranges, enums, contact guard, quota) without
 * writing; commit creates every draft in one transaction ({@code source='IMPORT'}) and only when no row has an error.
 * A file is committed at most once per owner (SHA-256 of its bytes): a repeated commit returns the first result.
 */
@Service
public class ListingImportService {
    public static final int MAX_ROWS = 200;
    public static final long MAX_BYTES = 1_048_576;
    public static final List<String> COLUMNS = List.of("title", "purpose", "propertyType", "priceVnd", "areaM2", "bedrooms",
            "bathrooms", "floors", "direction", "legalStatusCode", "legalStatus", "furnishing", "monthlyServiceFeeVnd",
            "depositVnd", "provinceCode", "districtCode", "wardCode", "addressSummary", "description");
    private static final List<String> REQUIRED = List.of("title", "purpose", "propertyType", "priceVnd", "areaM2");
    private static final long MAX_PRICE = 10_000_000_000_000L; // 10 000 tỷ

    public record Issue(String field, String message) {}

    public record RowResult(int line, String status, String title, List<Issue> errors, List<String> warnings, UUID listingId) {}

    public record ImportReport(String fileSha256, boolean dryRun, boolean committed, boolean duplicate, UUID batchId,
                               int totalRows, int validRows, Integer quotaRemaining, List<Issue> fileErrors,
                               List<RowResult> rows) {}

    private record Parsed(int line, CreateListingDraftCommand command, List<Issue> errors, List<String> warnings, String title) {}

    private final ListingApplicationService listings;
    private final JdbcTemplate jdbc;
    private final boolean quotaEnforced;

    public ListingImportService(ListingApplicationService listings, JdbcTemplate jdbc,
                                @Value("${app.billing.quota-enforced:true}") boolean quotaEnforced) {
        this.listings = listings;
        this.jdbc = jdbc;
        this.quotaEnforced = quotaEnforced;
    }

    public static String template() {
        return String.join(",", COLUMNS) + "\r\n"
                + "\"Căn hộ 2 phòng ngủ ví dụ, hãy xóa dòng này\",SALE,APARTMENT,3950000000,72.5,2,2,,Đông Nam,PINK_BOOK,Sổ hồng riêng,BASIC,,,01,001,,\"Phường mẫu, Quận mẫu\",\"Mô tả hiện trạng, tiện ích, thời gian xem nhà\"\r\n";
    }

    @Transactional
    public ImportReport importCsv(UUID ownerId, byte[] bytes, boolean dryRun) {
        String sha = sha256(bytes);
        List<Issue> fileErrors = new ArrayList<>();
        List<Parsed> parsed = new ArrayList<>();
        try {
            parsed = parse(ownerId, decode(bytes), fileErrors);
        } catch (IllegalArgumentException ex) {
            fileErrors.add(new Issue(null, ex.getMessage()));
        }
        Integer quota = quotaEnforced ? jdbc.queryForObject(
                "SELECT listing_quota_remaining FROM users WHERE id=?", Integer.class, ownerId) : null;
        if (quota != null) {
            for (int i = quota; i < parsed.size(); i++) {
                parsed.get(i).warnings().add("Vượt số lượt đăng còn lại (" + quota + "): tin được tạo nháp nhưng cần thêm lượt để gửi duyệt.");
            }
        }
        List<UUID> existing = committedBatch(ownerId, sha);
        boolean duplicate = !existing.isEmpty();
        int valid = (int) parsed.stream().filter(p -> p.errors().isEmpty()).count();
        boolean clean = fileErrors.isEmpty() && valid == parsed.size() && !parsed.isEmpty();

        if (dryRun || duplicate || !clean) {
            UUID batchId = duplicate ? existing.get(0) : null;
            List<UUID> ids = duplicate ? jdbc.queryForList(
                    "SELECT id FROM listings WHERE import_batch_id=? ORDER BY created_at, id", UUID.class, batchId) : List.of();
            return report(sha, dryRun, duplicate, duplicate, batchId, parsed, valid, quota, fileErrors, ids);
        }

        UUID batchId = UUID.randomUUID();
        int inserted = jdbc.update("""
                INSERT INTO listing_import_batches (id, owner_id, file_sha256, row_count, created_count)
                VALUES (?, ?, ?, ?, 0) ON CONFLICT (owner_id, file_sha256) DO NOTHING
                """, batchId, ownerId, sha, parsed.size());
        if (inserted == 0) { // a concurrent commit of the same file won
            UUID winner = committedBatch(ownerId, sha).get(0);
            List<UUID> ids = jdbc.queryForList("SELECT id FROM listings WHERE import_batch_id=? ORDER BY created_at, id", UUID.class, winner);
            return report(sha, false, true, true, winner, parsed, valid, quota, fileErrors, ids);
        }
        List<UUID> created = new ArrayList<>();
        for (Parsed row : parsed) {
            created.add(listings.createImportedDraft(row.command(), batchId).listingId());
        }
        jdbc.update("UPDATE listing_import_batches SET created_count=? WHERE id=?", created.size(), batchId);
        return report(sha, false, true, false, batchId, parsed, valid, quota, fileErrors, created);
    }

    private List<UUID> committedBatch(UUID ownerId, String sha) {
        return jdbc.queryForList("SELECT id FROM listing_import_batches WHERE owner_id=? AND file_sha256=?", UUID.class, ownerId, sha);
    }

    private static ImportReport report(String sha, boolean dryRun, boolean committed, boolean duplicate, UUID batchId,
                                       List<Parsed> parsed, int valid, Integer quota, List<Issue> fileErrors, List<UUID> ids) {
        List<RowResult> rows = new ArrayList<>();
        for (int i = 0; i < parsed.size(); i++) {
            Parsed p = parsed.get(i);
            rows.add(new RowResult(p.line(), p.errors().isEmpty() ? "VALID" : "INVALID", p.title(), p.errors(),
                    p.warnings(), committed && i < ids.size() ? ids.get(i) : null));
        }
        return new ImportReport(sha, dryRun, committed, duplicate, batchId, parsed.size(), valid, quota, fileErrors, rows);
    }

    private List<Parsed> parse(UUID ownerId, String text, List<Issue> fileErrors) {
        List<CsvReader.Line> lines = CsvReader.parse(text);
        if (lines.isEmpty()) {
            fileErrors.add(new Issue(null, "Tệp không có dữ liệu."));
            return List.of();
        }
        List<String> header = lines.get(0).cells().stream().map(h -> h.strip()).toList();
        for (String required : REQUIRED) {
            if (!header.contains(required)) fileErrors.add(new Issue(required, "Thiếu cột bắt buộc \"" + required + "\"."));
        }
        for (String column : header) {
            if (!column.isEmpty() && !COLUMNS.contains(column)) fileErrors.add(new Issue(column, "Cột không được hỗ trợ: \"" + column + "\"."));
        }
        if (lines.size() - 1 > MAX_ROWS) {
            fileErrors.add(new Issue(null, "Tối đa " + MAX_ROWS + " tin mỗi tệp (tệp có " + (lines.size() - 1) + ")."));
            return List.of();
        }
        if (!fileErrors.isEmpty()) return List.of();
        List<Parsed> rows = new ArrayList<>();
        for (CsvReader.Line line : lines.subList(1, lines.size())) {
            rows.add(parseRow(ownerId, header, line));
        }
        return rows;
    }

    private Parsed parseRow(UUID ownerId, List<String> header, CsvReader.Line line) {
        Row r = new Row(header, line.cells());
        List<Issue> e = new ArrayList<>();
        String title = r.text("title", 200, e);
        if (title == null) e.add(new Issue("title", "Bắt buộc."));
        else if (title.length() < 10) e.add(new Issue("title", "Tiêu đề từ 10 ký tự."));
        ListingPurpose purpose = r.enumValue("purpose", ListingPurpose.class, true, e);
        PropertyType type = r.enumValue("propertyType", PropertyType.class, true, e);
        Long price = r.integer("priceVnd", 0, MAX_PRICE, true, e);
        BigDecimal area = r.decimal("areaM2", new BigDecimal("1"), new BigDecimal("100000"), true, e);
        Long bedrooms = r.integer("bedrooms", 0, 50, false, e);
        Long bathrooms = r.integer("bathrooms", 0, 50, false, e);
        Long floors = r.integer("floors", 0, 200, false, e);
        String direction = r.text("direction", 30, e);
        LegalStatusCode legalCode = r.enumValue("legalStatusCode", LegalStatusCode.class, false, e);
        String legalText = r.text("legalStatus", 100, e);
        Furnishing furnishing = r.enumValue("furnishing", Furnishing.class, false, e);
        Long fee = r.integer("monthlyServiceFeeVnd", 0, 1_000_000_000L, false, e);
        Long deposit = r.integer("depositVnd", 0, MAX_PRICE, false, e);
        String province = r.text("provinceCode", 50, e);
        String district = r.text("districtCode", 50, e);
        String ward = r.text("wardCode", 50, e);
        String address = r.text("addressSummary", 255, e);
        String description = r.text("description", 5000, e);
        if (purpose == ListingPurpose.SALE) {
            if (fee != null) e.add(new Issue("monthlyServiceFeeVnd", "Chỉ áp dụng cho tin cho thuê."));
            if (deposit != null) e.add(new Issue("depositVnd", "Chỉ áp dụng cho tin cho thuê."));
        }
        if (legalCode == LegalStatusCode.OTHER && legalText == null) e.add(new Issue("legalStatus", "Mô tả giấy tờ khi chọn OTHER."));
        String[][] texts = {{"title", title}, {"description", description}, {"addressSummary", address},
                {"direction", direction}, {"legalStatus", legalText}};
        for (String[] t : texts) {
            if (ContactInfoGuard.containsContact(t[1])) e.add(new Issue(t[0], ContactInfoGuard.REJECTION));
        }
        CreateListingDraftCommand command = null;
        if (e.isEmpty()) {
            ListingAttributes attributes = new ListingAttributes(fee, deposit, furnishing, legalCode, null);
            try {
                listings.validateAttributes(purpose, legalText, attributes);
            } catch (ListingValidationException ex) {
                ex.issues().forEach(i -> e.add(new Issue(i.field(), i.message())));
            }
            command = new CreateListingDraftCommand(ownerId, title, purpose, type, price, area,
                    toInt(bedrooms), toInt(bathrooms), toInt(floors), null, null, direction, legalText, description,
                    province, district, ward, address, null, null, null, attributes);
        }
        return new Parsed(line.number(), command, e, new ArrayList<>(), title);
    }

    private static Integer toInt(Long value) { return value == null ? null : value.intValue(); }

    private static final class Row {
        private final List<String> header;
        private final List<String> cells;

        Row(List<String> header, List<String> cells) { this.header = header; this.cells = cells; }

        String raw(String column) {
            int index = header.indexOf(column);
            if (index < 0 || index >= cells.size()) return null;
            String value = cells.get(index).strip();
            return value.isEmpty() ? null : value;
        }

        String text(String column, int max, List<Issue> errors) {
            String value = raw(column);
            if (value != null && value.length() > max) errors.add(new Issue(column, "Tối đa " + max + " ký tự."));
            return value;
        }

        <E extends Enum<E>> E enumValue(String column, Class<E> type, boolean required, List<Issue> errors) {
            String value = raw(column);
            if (value == null) {
                if (required) errors.add(new Issue(column, "Bắt buộc."));
                return null;
            }
            try {
                return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                List<String> allowed = new ArrayList<>();
                for (E constant : type.getEnumConstants()) allowed.add(constant.name());
                errors.add(new Issue(column, "Giá trị hợp lệ: " + String.join(", ", allowed) + "."));
                return null;
            }
        }

        Long integer(String column, long min, long max, boolean required, List<Issue> errors) {
            String value = raw(column);
            if (value == null) {
                if (required) errors.add(new Issue(column, "Bắt buộc."));
                return null;
            }
            String digits = value.replace(".", "").replace(",", "").replace(" ", "");
            try {
                long parsed = Long.parseLong(digits);
                if (parsed < min || parsed > max) {
                    errors.add(new Issue(column, "Ngoài khoảng " + min + "–" + max + "."));
                    return null;
                }
                return parsed;
            } catch (NumberFormatException ex) {
                errors.add(new Issue(column, "Phải là số nguyên (VNĐ, không có đơn vị)."));
                return null;
            }
        }

        BigDecimal decimal(String column, BigDecimal min, BigDecimal max, boolean required, List<Issue> errors) {
            String value = raw(column);
            if (value == null) {
                if (required) errors.add(new Issue(column, "Bắt buộc."));
                return null;
            }
            try {
                BigDecimal parsed = new BigDecimal(value.replace(",", "."));
                if (parsed.compareTo(min) < 0 || parsed.compareTo(max) > 0) {
                    errors.add(new Issue(column, "Ngoài khoảng " + min + "–" + max + "."));
                    return null;
                }
                return parsed;
            } catch (NumberFormatException ex) {
                errors.add(new Issue(column, "Phải là số (dùng dấu chấm hoặc phẩy thập phân)."));
                return null;
            }
        }
    }

    private static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException ex) {
            throw new IllegalArgumentException("Tệp phải được lưu với mã hóa UTF-8.");
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
