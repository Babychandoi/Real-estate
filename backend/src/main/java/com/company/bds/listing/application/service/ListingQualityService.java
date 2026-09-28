package com.company.bds.listing.application.service;

import com.company.bds.listing.domain.model.LegalStatusCode;
import com.company.bds.listing.domain.model.ListingPurpose;
import com.company.bds.listing.domain.model.ListingRevision;
import com.company.bds.listing.domain.model.PropertyType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Listing quality checklist (P-08): guidance for the poster, never a gate. Price per m² is compared with the median of
 * published listings of the same district, purpose and type only when at least {@link #MIN_COMPARABLES} exist;
 * otherwise the item says "chưa đủ dữ liệu" instead of inventing a benchmark.
 */
@Service
public class ListingQualityService {
    public static final int MIN_IMAGES = 5;
    public static final int MIN_DESCRIPTION = 200;
    public static final int MIN_COMPARABLES = 10;
    /** Accepted band around the district median price per m². */
    static final double LOW_FACTOR = 1d / 3d;
    static final double HIGH_FACTOR = 3d;

    public enum Status { PASS, WARN, NO_DATA }

    public record Item(String code, String label, Status status, String hint) {}

    public record Report(int passed, int total, List<Item> items) {}

    public record Input(UUID key, ListingPurpose purpose, PropertyType propertyType, long priceVnd, BigDecimal areaM2,
                        int imageCount, String description, String districtCode, Double latitude, Double longitude,
                        LegalStatusCode legalStatusCode, Long depositVnd, Long monthlyServiceFeeVnd) {
        public static Input of(UUID key, ListingRevision r) {
            return new Input(key, r.getPurpose(), r.getPropertyType(), r.getPriceVnd(), r.getAreaM2(), r.getMediaList().size(),
                    r.getDescription(), r.getDistrictCode(), r.getPublicLatitude(), r.getPublicLongitude(),
                    r.getAttributes().legalStatusCode(), r.getAttributes().depositVnd(), r.getAttributes().monthlyServiceFeeVnd());
        }
    }

    record Benchmark(long count, double medianPerM2) {}

    private final JdbcTemplate jdbc;

    public ListingQualityService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Report evaluate(Input input) {
        return evaluateAll(List.of(input)).get(input.key());
    }

    /** One benchmark query for all inputs (bounded by the number of distinct district/purpose/type groups). */
    public Map<UUID, Report> evaluateAll(List<Input> inputs) {
        Map<String, Benchmark> benchmarks = benchmarks(inputs);
        Map<UUID, Report> reports = new LinkedHashMap<>();
        for (Input input : inputs) reports.put(input.key(), report(input, benchmarks.get(groupKey(input))));
        return reports;
    }

    Report report(Input in, Benchmark benchmark) {
        List<Item> items = new ArrayList<>();
        items.add(in.imageCount() >= MIN_IMAGES
                ? new Item("IMAGES", "Có ít nhất 5 ảnh", Status.PASS, null)
                : new Item("IMAGES", "Có ít nhất 5 ảnh", Status.WARN,
                        "Tin hiện có " + in.imageCount() + " ảnh. Thêm ảnh phòng khách, phòng ngủ, bếp, mặt tiền và giấy tờ (che thông tin cá nhân)."));
        int descriptionLength = in.description() == null ? 0 : in.description().strip().length();
        items.add(descriptionLength >= MIN_DESCRIPTION
                ? new Item("DESCRIPTION", "Mô tả từ 200 ký tự", Status.PASS, null)
                : new Item("DESCRIPTION", "Mô tả từ 200 ký tự", Status.WARN,
                        "Mô tả hiện có " + descriptionLength + " ký tự. Nêu hiện trạng, hướng, tiện ích, thời gian xem nhà."));
        boolean located = in.districtCode() != null && !in.districtCode().isBlank() && in.latitude() != null && in.longitude() != null;
        items.add(located
                ? new Item("LOCATION", "Có quận/huyện và vị trí trên bản đồ", Status.PASS, null)
                : new Item("LOCATION", "Có quận/huyện và vị trí trên bản đồ", Status.WARN,
                        "Chọn quận/huyện và đánh dấu vị trí gần đúng trên bản đồ."));
        items.add(in.legalStatusCode() != null
                ? new Item("LEGAL", "Có loại giấy tờ pháp lý", Status.PASS, null)
                : new Item("LEGAL", "Có loại giấy tờ pháp lý", Status.WARN, "Chọn loại giấy tờ (sổ đỏ, sổ hồng, HĐMB…)."));
        if (in.purpose() == ListingPurpose.RENT) {
            items.add(in.depositVnd() != null
                    ? new Item("RENT_TERMS", "Có điều kiện thuê (tiền cọc)", Status.PASS, null)
                    : new Item("RENT_TERMS", "Có điều kiện thuê (tiền cọc)", Status.WARN,
                            "Ghi tiền đặt cọc và phí dịch vụ hằng tháng (nếu có)."));
        }
        items.add(priceItem(in, benchmark));
        int passed = (int) items.stream().filter(item -> item.status() == Status.PASS).count();
        return new Report(passed, items.size(), items);
    }

    private Item priceItem(Input in, Benchmark benchmark) {
        String label = "Giá/m² hợp lý so với khu vực";
        if (in.areaM2() == null || in.areaM2().signum() <= 0 || in.priceVnd() <= 0) {
            return new Item("PRICE_PLAUSIBILITY", label, Status.WARN, "Nhập giá và diện tích để so sánh.");
        }
        if (benchmark == null || benchmark.count() < MIN_COMPARABLES || benchmark.medianPerM2() <= 0) {
            return new Item("PRICE_PLAUSIBILITY", label, Status.NO_DATA,
                    "Chưa đủ dữ liệu: cần ít nhất " + MIN_COMPARABLES + " tin cùng loại trong quận/huyện để so sánh.");
        }
        double perM2 = in.priceVnd() / in.areaM2().doubleValue();
        double ratio = perM2 / benchmark.medianPerM2();
        String median = millions(benchmark.medianPerM2());
        if (ratio >= LOW_FACTOR && ratio <= HIGH_FACTOR) {
            return new Item("PRICE_PLAUSIBILITY", label, Status.PASS,
                    "Trung vị " + benchmark.count() + " tin cùng loại trong quận: " + median + (in.purpose() == ListingPurpose.RENT ? "/m²/tháng." : "/m²."));
        }
        return new Item("PRICE_PLAUSIBILITY", label, Status.WARN,
                "Giá/m² " + (ratio < LOW_FACTOR ? "thấp" : "cao") + " bất thường so với trung vị " + benchmark.count()
                        + " tin cùng loại trong quận (" + median + "/m²). Kiểm tra lại đơn vị giá (tỷ/triệu) và diện tích.");
    }

    private static String millions(double vnd) {
        return BigDecimal.valueOf(vnd / 1_000_000d).setScale(1, RoundingMode.HALF_UP).toPlainString().replace('.', ',') + " triệu";
    }

    private static String groupKey(Input in) {
        return in.districtCode() + "|" + in.purpose() + "|" + in.propertyType();
    }

    private Map<String, Benchmark> benchmarks(List<Input> inputs) {
        Set<Input> groups = new LinkedHashSet<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Input in : inputs) {
            if (in.districtCode() == null || in.districtCode().isBlank() || in.purpose() == null || in.propertyType() == null) continue;
            if (seen.add(groupKey(in))) groups.add(in);
        }
        Map<String, Benchmark> result = new HashMap<>();
        if (groups.isEmpty()) return result;
        StringBuilder values = new StringBuilder();
        List<Object> args = new ArrayList<>();
        for (Input in : groups) {
            if (!values.isEmpty()) values.append(',');
            values.append("(?,?,?)");
            args.add(in.districtCode());
            args.add(in.purpose().name());
            args.add(in.propertyType().name());
        }
        String sql = """
                SELECT r.district_code, r.purpose, r.property_type, COUNT(*) AS n,
                       percentile_cont(0.5) WITHIN GROUP (ORDER BY r.price_vnd / r.area_m2) AS median
                FROM listings l JOIN listing_revisions r ON r.id = l.public_revision_id
                WHERE l.status = 'ACTIVE' AND r.area_m2 > 0 AND r.price_vnd > 0
                  AND (r.district_code, r.purpose, r.property_type) IN (%s)
                GROUP BY r.district_code, r.purpose, r.property_type
                """.formatted(values);
        jdbc.query(sql, rs -> {
            result.put(rs.getString(1) + "|" + rs.getString(2) + "|" + rs.getString(3),
                    new Benchmark(rs.getLong(4), rs.getDouble(5)));
        }, args.toArray());
        return result;
    }
}
