package za.co.ntsumele.bank.dispute.disputeDto;

/**
 * class : DisputeCountsResponse
 * Author : Nyiko Chauke
 * 2026
 *
 * Returns total, active, awaiting-information and resolved case counts for the authorized workspace.
 * These aggregates do not depend on which page of cases is currently displayed.
 */
public record DisputeCountsResponse(long total, long active, long awaitingInformation, long resolved) {
}
