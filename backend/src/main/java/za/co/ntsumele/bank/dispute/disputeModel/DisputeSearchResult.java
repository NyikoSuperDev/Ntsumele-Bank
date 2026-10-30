package za.co.ntsumele.bank.dispute.disputeModel;

import java.util.List;

/**
 * class : DisputeSearchResult
 * Author : Nyiko Chauke
 * 2026
 *
 * Carries a bounded page of stored cases and its matching pagination metadata inside the dispute feature.
 * The immutable list is enriched with batched transaction views and activity counts by the inquiry service.
 */
public record DisputeSearchResult(
        List<DisputeCase> content, long totalElements, int totalPages, int number, int size) {
    public DisputeSearchResult {
        content = List.copyOf(content);
    }
}
