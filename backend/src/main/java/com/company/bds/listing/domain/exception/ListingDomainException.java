package com.company.bds.listing.domain.exception;

/**
 * Ngoại lệ nghiệp vụ vi phạm invariant của Aggregate Listing.
 * Tuân thủ quy ước: Domain Exception thuần Java, không chứa mã HTTP.
 */
public class ListingDomainException extends RuntimeException {

    private final String errorCode;

    public ListingDomainException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
