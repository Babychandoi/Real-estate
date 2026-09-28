package com.company.bds.search.application;

import java.util.Map;

/** A search/listing-read failure answered with Problem Details carrying a stable {@code code}. */
public class SearchProblemException extends RuntimeException {
    private final int status;
    private final String code;
    private final String title;
    private final transient Map<String, Object> extra;

    public SearchProblemException(int status, String code, String title, String detail) {
        this(status, code, title, detail, Map.of());
    }

    public SearchProblemException(int status, String code, String title, String detail, Map<String, Object> extra) {
        super(detail);
        this.status = status;
        this.code = code;
        this.title = title;
        this.extra = Map.copyOf(extra);
    }

    public int status() { return status; }

    public String code() { return code; }

    public String title() { return title; }

    /** Extra top-level members (e.g. {@code slug}/{@code title} of a listing that is no longer public). */
    public Map<String, Object> extra() { return extra; }
}
