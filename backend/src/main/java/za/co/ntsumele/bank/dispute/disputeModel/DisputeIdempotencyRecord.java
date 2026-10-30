package za.co.ntsumele.bank.dispute.disputeModel;

import java.time.Instant;

/**
 * class : DisputeIdempotencyRecord
 * Author : Nyiko Chauke
 * 2026
 *
 * Links a customer request key and payload hash to the dispute created for that request.
 * Allows the submission service to distinguish safe replays from conflicting key reuse.
 */
public record DisputeIdempotencyRecord(
        String username,
        String requestKey,
        String payloadHash,
        String disputeId,
        Instant createdAt) {
}
