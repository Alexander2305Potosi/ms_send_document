package com.example.fileprocessor.domain.usecase;

/**
 * Result of a document claim operation.
 *
 * @param claimed true if this instance claimed the document
 * @param previousStatus status before the claim (PENDING, PROCESSING, RETRY)
 * @param attemptCount total number of claim attempts for this document
 */
public record ClaimResult(
    boolean claimed,
    String previousStatus,
    int attemptCount
) {
    public static final ClaimResult NOT_CLAIMED = new ClaimResult(false, null, 0);

    /**
     * Indicates if the claim was a RECOVERY (document was stuck in PROCESSING or RETRY).
     */
    public boolean isRecovery() {
        return "PROCESSING".equals(previousStatus) || "RETRY".equals(previousStatus);
    }
}
