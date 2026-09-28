package com.company.bds.listing.domain.exception;

/** The draft changed since the client loaded it (another tab/device saved first); mapped to 409 VERSION_CONFLICT. */
public class ListingVersionConflictException extends RuntimeException {
    private final Long currentVersion;

    public ListingVersionConflictException(Long currentVersion) {
        super("Tin đăng vừa được lưu ở nơi khác. Tải lại bản mới nhất trước khi lưu tiếp.");
        this.currentVersion = currentVersion;
    }

    public Long currentVersion() { return currentVersion; }
}
