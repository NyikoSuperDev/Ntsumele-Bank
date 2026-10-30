package za.co.ntsumele.bank.dispute.disputeModel;

/**
 * class : DisputeCaseCounts
 * Author : Nyiko Chauke
 * 2026
 *
 * Holds database aggregate counts for the current customer entitlement scope or the staff queue.
 */
public record DisputeCaseCounts(long total, long active, long awaitingInformation, long resolved) {
}
