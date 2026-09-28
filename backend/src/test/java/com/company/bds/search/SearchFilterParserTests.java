package com.company.bds.search;

import com.company.bds.search.domain.InvalidFilterException;
import com.company.bds.search.domain.SearchFilter;
import com.company.bds.search.domain.SearchFilterParser;
import com.company.bds.search.domain.SearchSort;
import com.company.bds.search.domain.VietnameseNormalizer;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Filter schema of contract §7: strict validation (F06.1), canonical form and hash (F03.1). */
class SearchFilterParserTests {

    private static Map<String, String[]> params(String... pairs) {
        Map<String, String[]> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.merge(pairs[i], new String[]{pairs[i + 1]}, (a, b) -> {
                String[] joined = new String[a.length + b.length];
                System.arraycopy(a, 0, joined, 0, a.length);
                System.arraycopy(b, 0, joined, a.length, b.length);
                return joined;
            });
        }
        return map;
    }

    private static InvalidFilterException invalid(Map<String, String[]> params) {
        try {
            SearchFilterParser.parse(params);
        } catch (InvalidFilterException ex) {
            return ex;
        }
        throw new AssertionError("expected INVALID_FILTER for " + params.keySet());
    }

    @Test
    void defaultsArePurposeSaleSortNewestSize24() {
        SearchFilterParser.SearchRequest request = SearchFilterParser.parse(Map.of());
        assertThat(request.filter().purpose()).isEqualTo("SALE");
        assertThat(request.filter().sort()).isEqualTo(SearchSort.NEWEST);
        assertThat(request.size()).isEqualTo(24);
        assertThat(request.cursor()).isNull();
    }

    @Test
    void keywordDefaultsToRelevanceAndIsNormalised() {
        SearchFilter filter = SearchFilterParser.parse(params("q", "  Căn hộ  CẦU GIẤY, 3PN ")).filter();
        assertThat(filter.keyword()).isEqualTo("can ho cau giay 3pn");
        assertThat(filter.sort()).isEqualTo(SearchSort.RELEVANCE);
    }

    @Test
    void everyInvalidValueIsReportedAtOnce() {
        InvalidFilterException ex = invalid(params(
                "purpose", "BUY", "type", "APARTMENT,CASTLE", "priceMin", "5", "priceMax", "4", "areaMin", "-1",
                "bedsMin", "11", "legal", "BLUE_BOOK", "furnishing", "LUXURY", "verified", "YES", "district", "5",
                "project", "not-a-uuid", "sort", "CHEAPEST", "size", "49", "view", "grid", "foo", "bar"));
        assertThat(ex.code()).isEqualTo("INVALID_FILTER");
        assertThat(ex.errors()).extracting(InvalidFilterException.FilterError::param).contains(
                "purpose", "type", "priceMax", "areaMin", "bedsMin", "legal", "furnishing", "verified", "district",
                "project", "sort", "size", "view", "foo");
    }

    @Test
    void rangesAndBboxAreChecked() {
        assertThat(invalid(params("areaMin", "90", "areaMax", "50")).errors()).extracting("param").containsExactly("areaMax");
        assertThat(invalid(params("priceMin", "1.5")).errors()).extracting("param").containsExactly("priceMin");
        assertThat(invalid(params("bbox", "105.9,21.0,105.8,21.1")).errors()).extracting("param").containsExactly("bbox");
        assertThat(invalid(params("bbox", "105,21,106")).errors()).extracting("param").containsExactly("bbox");
        assertThat(invalid(params("bbox", "100,20,104,21")).errors()).extracting("message")
                .anySatisfy(message -> assertThat((String) message).contains("3 độ"));
        assertThat(invalid(params("sort", "RELEVANCE")).errors()).extracting("param").containsExactly("sort");
        assertThat(invalid(params("q", "!!!")).errors()).extracting("param").containsExactly("q");
        assertThat(invalid(params("q", "x".repeat(101))).errors()).extracting("param").containsExactly("q");
        assertThat(invalid(params("purpose", "SALE", "purpose", "RENT")).errors()).extracting("param").containsExactly("purpose");
    }

    @Test
    void bboxIsRoundedToFiveDecimals() {
        SearchFilter filter = SearchFilterParser.parse(params("bbox", "105.7812345,21.0000049,105.80,21.05")).filter();
        assertThat(filter.bbox().canonical()).isEqualTo("105.78123,21,105.8,21.05");
    }

    @Test
    void canonicalHashIgnoresOrderCsvOrderPagingAndUiParams() {
        SearchFilter a = SearchFilterParser.parse(params("type", "HOUSE,APARTMENT", "district", "006", "district", "005",
                "areaMin", "50.50", "q", "Cầu Giấy", "size", "12", "view", "map", "place", "Cầu Giấy")).filter();
        SearchFilter b = SearchFilterParser.parse(params("q", "cau giay", "areaMin", "50.5", "district", "005,006",
                "type", "APARTMENT,HOUSE")).filter();
        assertThat(a.filterHash()).isEqualTo(b.filterHash()).hasSize(32);
        assertThat(a.canonicalParams()).containsEntry("type", "APARTMENT,HOUSE").containsEntry("district", "005,006")
                .containsEntry("areaMin", "50.5").containsEntry("q", "cau giay").containsEntry("sort", "RELEVANCE")
                .doesNotContainKeys("size", "view", "place", "cursor");
        SearchFilter rent = SearchFilterParser.parse(params("purpose", "RENT", "type", "APARTMENT,HOUSE", "district", "005,006",
                "areaMin", "50.5", "q", "cau giay")).filter();
        assertThat(rent.filterHash()).isNotEqualTo(a.filterHash());
    }

    @Test
    void hashOfTheCanonicalJsonIsStable() {
        // The frontend filter schema computes the same value (filterSchema.test.ts uses this vector).
        SearchFilter filter = SearchFilterParser.parse(params("purpose", "RENT", "priceMax", "15000000", "bedsMin", "2")).filter();
        assertThat(filter.canonicalParams().toString()).isEqualTo("{bedsMin=2, priceMax=15000000, purpose=RENT, sort=NEWEST}");
        assertThat(filter.filterHash()).isEqualTo(sha256Prefix("{\"bedsMin\":\"2\",\"priceMax\":\"15000000\",\"purpose\":\"RENT\",\"sort\":\"NEWEST\"}"));
    }

    @Test
    void normaliserFoldsVietnameseLikeTheIndex() {
        assertThat(VietnameseNormalizer.normalize("Đường Nguyễn Trãi – Thanh Xuân")).isEqualTo("duong nguyen trai thanh xuan");
        assertThat(VietnameseNormalizer.normalize(java.text.Normalizer.normalize("Hoà Bình", java.text.Normalizer.Form.NFD)))
                .isEqualTo("hoa binh");
        assertThat(VietnameseNormalizer.normalize(" - ")).isNull();
    }

    @Test
    void unknownParameterIsRejectedButEndpointExtrasAreAllowed() {
        assertThatThrownBy(() -> SearchFilterParser.parse(params("zoom", "12"))).isInstanceOf(InvalidFilterException.class);
        assertThat(SearchFilterParser.parse(params("zoom", "12"), java.util.Set.of("zoom")).filter().purpose()).isEqualTo("SALE");
    }

    static String sha256Prefix(String text) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest).substring(0, 32);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
