package com.company.bds.search.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Search text normalisation, version {@value #VERSION} (audit D-08). Must stay identical to the SQL function
 * {@code bds_search_normalize} (V033) and to {@code normalizeKeyword} in the frontend filter schema: NFD, drop combining
 * marks, {@code đ→d}, lower case, every run of characters outside {@code [a-z0-9]} becomes one space, trimmed.
 * The Elasticsearch analyzer ({@code standard} tokenizer + {@code lowercase} + {@code asciifolding}) yields the same
 * tokens for text normalised this way.
 */
public final class VietnameseNormalizer {
    public static final int VERSION = 1;
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_TOKEN = Pattern.compile("[^a-z0-9]+");

    private VietnameseNormalizer() {}

    /** Normalised text, or {@code null} when nothing searchable remains. */
    public static String normalize(String text) {
        if (text == null) return null;
        String decomposed = Normalizer.normalize(text.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD);
        String folded = MARKS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT);
        String collapsed = NON_TOKEN.matcher(folded).replaceAll(" ").trim();
        return collapsed.isEmpty() ? null : collapsed;
    }
}
