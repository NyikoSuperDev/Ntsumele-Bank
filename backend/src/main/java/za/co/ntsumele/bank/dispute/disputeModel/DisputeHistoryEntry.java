package za.co.ntsumele.bank.dispute.disputeModel;

import java.time.Instant;

/**
 * class : DisputeHistoryEntry
 * Author : Nyiko Chauke
 * 2026
 *
 * Represents a retained case activity event with its status change, actor, time and note.
 * A null previous status identifies the initial submission event.
 */
public record DisputeHistoryEntry(
        String id,
        String disputeId,
        DisputeStatus fromStatus,
        DisputeStatus toStatus,
        String actor,
        Instant occurredAt,
        String note) {
}
