package za.co.ntsumele.bank.dispute.disputeModel;

import java.time.Instant;

/**
 * class : DisputeCase
 * Author : Nyiko Chauke
 * 2026
 *
 * Represents a dispute case state, submitter, transaction reference, resolution and concurrency version.
 * A dispute references a ledger transaction and never owns its financial values.
 */
public record DisputeCase(
        String id,
        String caseReference,
        String transactionId,
        String submittedBy,
        DisputeReason reason,
        String description,
        DisputeStatus status,
        Instant createdAt,
        Instant updatedAt,
        String resolution,
        long version) {
}
