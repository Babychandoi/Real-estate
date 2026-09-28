package com.company.bds.search.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Parses and validates the search parameters of contract §7. Every problem is reported (one error per parameter),
 * unknown parameters included: nothing is silently ignored. Repeated parameters are merged like a CSV.
 */
public final class SearchFilterParser {
    public static final int DEFAULT_SIZE = 24;
    public static final int MAX_SIZE = 48;
    public static final int MAX_KEYWORD_LENGTH = 100;
    public static final long MAX_PRICE_VND = 1_000_000_000_000_000L;
    public static final BigDecimal MAX_AREA_M2 = new BigDecimal("1000000");
    public static final Set<String> PURPOSES = Set.of("SALE", "RENT");
    public static final Set<String> TYPES = Set.of("APARTMENT", "HOUSE", "VILLA", "TOWNHOUSE", "LAND");
    public static final Set<String> LEGAL = Set.of("RED_BOOK", "PINK_BOOK", "SALE_CONTRACT", "PENDING_CERTIFICATE", "OTHER");
    public static final Set<String> FURNISHING = Set.of("NONE", "BASIC", "FULL");
    public static final Set<String> VERIFIED = Set.of("IDENTITY", "OWNERSHIP");
    /** Filter parameters plus the API-only {@code size}/{@code cursor} and the UI-only {@code view}/{@code place}. */
    public static final Set<String> SEARCH_PARAMS = Set.of("purpose", "type", "priceMin", "priceMax", "areaMin", "areaMax",
            "bedsMin", "legal", "furnishing", "verified", "district", "project", "q", "bbox", "sort", "size", "cursor",
            "view", "place");
    private static final Pattern DISTRICT = Pattern.compile("\\d{3}");
    private static final Pattern INTEGER = Pattern.compile("\\d{1,19}");
    private static final Pattern DECIMAL = Pattern.compile("\\d{1,7}(\\.\\d{1,2})?");
    private static final int MAX_DISTRICTS = 30;

    /** A parsed request: the filter plus paging parameters. */
    public record SearchRequest(SearchFilter filter, int size, String cursor, boolean sortRequested) {}

    private SearchFilterParser() {}

    public static SearchRequest parse(Map<String, String[]> raw) {
        return parse(raw, Set.of());
    }

    /**
     * @param extraParams endpoint-specific parameters validated by the caller (e.g. {@code zoom} of the map endpoint)
     */
    public static SearchRequest parse(Map<String, String[]> raw, Set<String> extraParams) {
        Errors errors = new Errors();
        for (String name : raw.keySet()) {
            if (!SEARCH_PARAMS.contains(name) && !extraParams.contains(name)) errors.add(name, "Tham số không được hỗ trợ.");
        }
        String purpose = single(raw, "purpose", errors);
        if (purpose == null) purpose = "SALE";
        else if (!PURPOSES.contains(purpose)) errors.add("purpose", "Giá trị phải là SALE hoặc RENT.");

        SortedSet<String> types = csv(raw, "type", TYPES, errors, "Loại hình không hợp lệ: ");
        Long priceMin = integer(raw, "priceMin", errors);
        Long priceMax = integer(raw, "priceMax", errors);
        if (priceMin != null && priceMax != null && priceMin > priceMax) {
            errors.add("priceMax", "Giá tối đa phải lớn hơn hoặc bằng giá tối thiểu.");
        }
        BigDecimal areaMin = decimal(raw, "areaMin", errors);
        BigDecimal areaMax = decimal(raw, "areaMax", errors);
        if (areaMin != null && areaMax != null && areaMin.compareTo(areaMax) > 0) {
            errors.add("areaMax", "Diện tích tối đa phải lớn hơn hoặc bằng diện tích tối thiểu.");
        }
        Integer bedsMin = null;
        String beds = single(raw, "bedsMin", errors);
        if (beds != null) {
            if (!beds.matches("\\d{1,2}") || Integer.parseInt(beds) < 1 || Integer.parseInt(beds) > 10) {
                errors.add("bedsMin", "Số phòng ngủ tối thiểu phải từ 1 đến 10.");
            } else {
                bedsMin = Integer.parseInt(beds);
            }
        }
        SortedSet<String> legal = csv(raw, "legal", LEGAL, errors, "Mã pháp lý không hợp lệ: ");
        SortedSet<String> furnishing = csv(raw, "furnishing", FURNISHING, errors, "Nội thất không hợp lệ: ");
        String verified = single(raw, "verified", errors);
        if (verified != null && !VERIFIED.contains(verified)) {
            errors.add("verified", "Giá trị phải là IDENTITY hoặc OWNERSHIP.");
            verified = null;
        }
        SortedSet<String> districts = new TreeSet<>();
        for (String value : values(raw, "district")) {
            if (!DISTRICT.matcher(value).matches()) errors.add("district", "Mã khu vực phải gồm 3 chữ số: " + value);
            else districts.add(value);
        }
        if (districts.size() > MAX_DISTRICTS) errors.add("district", "Tối đa " + MAX_DISTRICTS + " khu vực.");
        UUID project = null;
        String projectText = single(raw, "project", errors);
        if (projectText != null) {
            try {
                project = UUID.fromString(projectText);
                if (!project.toString().equals(projectText.toLowerCase())) throw new IllegalArgumentException();
            } catch (IllegalArgumentException ex) {
                errors.add("project", "Mã dự án không hợp lệ.");
                project = null;
            }
        }
        String keyword = null;
        String q = single(raw, "q", errors);
        if (q != null && !q.isBlank()) {
            if (q.length() > MAX_KEYWORD_LENGTH) errors.add("q", "Từ khóa tối đa " + MAX_KEYWORD_LENGTH + " ký tự.");
            else {
                keyword = VietnameseNormalizer.normalize(q);
                if (keyword == null) errors.add("q", "Từ khóa không có chữ hoặc số để tìm.");
            }
        }
        BoundingBox bbox = bbox(single(raw, "bbox", errors), errors, InvalidFilterException.INVALID_FILTER);
        SearchSort sort = null;
        String sortText = single(raw, "sort", errors);
        if (sortText != null) {
            try {
                sort = SearchSort.valueOf(sortText);
                if (sort == SearchSort.RELEVANCE && keyword == null) {
                    errors.add("sort", "Sắp xếp theo độ liên quan cần có từ khóa.");
                }
            } catch (IllegalArgumentException ex) {
                errors.add("sort", "Cách sắp xếp không hợp lệ.");
            }
        }
        boolean sortRequested = sort != null;
        if (sort == null) sort = keyword != null ? SearchSort.RELEVANCE : SearchSort.NEWEST;
        int size = DEFAULT_SIZE;
        String sizeText = single(raw, "size", errors);
        if (sizeText != null) {
            if (!sizeText.matches("\\d{1,3}") || Integer.parseInt(sizeText) < 1 || Integer.parseInt(sizeText) > MAX_SIZE) {
                errors.add("size", "Số tin mỗi trang phải từ 1 đến " + MAX_SIZE + ".");
            } else {
                size = Integer.parseInt(sizeText);
            }
        }
        String cursor = single(raw, "cursor", errors);
        if (cursor != null && (cursor.isBlank() || cursor.length() > 2000)) errors.add("cursor", "Con trỏ trang không hợp lệ.");
        String place = single(raw, "place", errors);
        if (place != null && place.length() > 200) errors.add("place", "Tên địa điểm tối đa 200 ký tự.");
        String view = single(raw, "view", errors);
        if (view != null && !Set.of("list", "map", "split").contains(view)) errors.add("view", "Chế độ xem không hợp lệ.");
        errors.throwIfAny(InvalidFilterException.INVALID_FILTER);
        SearchFilter filter = new SearchFilter(purpose, types, priceMin, priceMax, areaMin, areaMax, bedsMin, legal,
                furnishing, verified, districts, project, keyword, bbox, sort);
        return new SearchRequest(filter, size, cursor, sortRequested);
    }

    /**
     * Parses {@code minLng,minLat,maxLng,maxLat}; null when absent. The span rule reports {@code spanCode} so the map
     * endpoint can answer {@code BBOX_TOO_LARGE}.
     */
    public static BoundingBox bbox(String text, Errors errors, String spanCode) {
        if (text == null) return null;
        String[] parts = text.split(",", -1);
        if (parts.length != 4) {
            errors.add("bbox", "Khung bản đồ phải có dạng minLng,minLat,maxLng,maxLat.");
            return null;
        }
        double[] v = new double[4];
        for (int i = 0; i < 4; i++) {
            try {
                v[i] = BoundingBox.round5(Double.parseDouble(parts[i].trim()));
                if (!Double.isFinite(v[i])) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                errors.add("bbox", "Tọa độ khung bản đồ không hợp lệ.");
                return null;
            }
        }
        if (v[0] < -180 || v[2] > 180 || v[1] < -90 || v[3] > 90 || v[0] >= v[2] || v[1] >= v[3]) {
            errors.add("bbox", "Khung bản đồ nằm ngoài phạm vi hoặc sai thứ tự.");
            return null;
        }
        BoundingBox box = new BoundingBox(v[0], v[1], v[2], v[3]);
        if (box.lngSpan() > BoundingBox.MAX_SPAN_DEGREES || box.latSpan() > BoundingBox.MAX_SPAN_DEGREES) {
            errors.spanCode = spanCode;
            errors.add("bbox", "Khung bản đồ rộng tối đa " + (int) BoundingBox.MAX_SPAN_DEGREES + " độ; hãy phóng to bản đồ.");
            return null;
        }
        return box;
    }

    static SortedSet<String> sorted(Collection<String> values) {
        return values == null ? Collections.unmodifiableSortedSet(new TreeSet<>())
                : Collections.unmodifiableSortedSet(new TreeSet<>(values));
    }

    static String plain(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0).toPlainString() : stripped.toPlainString();
    }

    private static List<String> values(Map<String, String[]> raw, String name) {
        String[] given = raw.get(name);
        List<String> out = new ArrayList<>();
        if (given == null) return out;
        for (String value : given) {
            if (value == null) continue;
            for (String part : value.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) out.add(trimmed);
            }
        }
        return out;
    }

    private static String single(Map<String, String[]> raw, String name, Errors errors) {
        String[] given = raw.get(name);
        if (given == null || given.length == 0) return null;
        if (given.length > 1) {
            errors.add(name, "Tham số chỉ được xuất hiện một lần.");
            return null;
        }
        String value = given[0] == null ? "" : given[0].trim();
        if (value.isEmpty() && !"q".equals(name)) return null;
        return value;
    }

    private static SortedSet<String> csv(Map<String, String[]> raw, String name, Set<String> allowed, Errors errors,
                                         String message) {
        SortedSet<String> out = new TreeSet<>();
        for (String value : values(raw, name)) {
            if (!allowed.contains(value)) errors.add(name, message + value);
            else out.add(value);
        }
        return out;
    }

    private static Long integer(Map<String, String[]> raw, String name, Errors errors) {
        String value = single(raw, name, errors);
        if (value == null) return null;
        if (!INTEGER.matcher(value).matches()) {
            errors.add(name, "Giá phải là số nguyên VND không âm.");
            return null;
        }
        try {
            long parsed = Long.parseLong(value);
            if (parsed > MAX_PRICE_VND) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException ex) {
            errors.add(name, "Giá vượt quá giới hạn cho phép.");
            return null;
        }
    }

    private static BigDecimal decimal(Map<String, String[]> raw, String name, Errors errors) {
        String value = single(raw, name, errors);
        if (value == null) return null;
        if (!DECIMAL.matcher(value).matches()) {
            errors.add(name, "Diện tích phải là số dương, tối đa 2 chữ số thập phân.");
            return null;
        }
        BigDecimal parsed = new BigDecimal(value);
        if (parsed.signum() <= 0 || parsed.compareTo(MAX_AREA_M2) > 0) {
            errors.add(name, "Diện tích phải lớn hơn 0 và không quá 1.000.000 m².");
            return null;
        }
        return parsed;
    }

    /** Collects every error of one request. */
    public static final class Errors {
        private final List<InvalidFilterException.FilterError> list = new ArrayList<>();
        private String spanCode;

        public void add(String param, String message) { list.add(new InvalidFilterException.FilterError(param, message)); }

        public boolean isEmpty() { return list.isEmpty(); }

        public List<InvalidFilterException.FilterError> list() { return List.copyOf(list); }

        public void throwIfAny(String code) {
            if (list.isEmpty()) return;
            boolean onlySpan = spanCode != null && list.size() == 1;
            throw new InvalidFilterException(onlySpan ? spanCode : code, list);
        }
    }
}
