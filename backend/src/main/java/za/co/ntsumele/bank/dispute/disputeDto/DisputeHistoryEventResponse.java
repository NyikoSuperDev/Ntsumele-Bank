package za.co.ntsumele.bank.dispute.disputeDto;

import java.time.Instant;

/**
 * class : DisputeHistoryEventResponse
 * Author : Nyiko Chauke
 * 2026
 *
 * Presents one case activity entry with its previous and resulting statuses, actor, timestamp
 * and customer-visible note.
 */
public record DisputeHistoryEventResponse(
        String id,
        String fromStatus,
        String toStatus,
        String actor,
        Instant occurredAt,
        String note) {
}
