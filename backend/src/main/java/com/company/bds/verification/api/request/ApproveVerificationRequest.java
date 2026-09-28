package com.company.bds.verification.api.request;

/** {@code reasonCode}: a TrustReason of kind APPROVE (default DOCUMENTS_MATCH). */
public record ApproveVerificationRequest(
        String verifierNote,
        String reasonCode
) {}
