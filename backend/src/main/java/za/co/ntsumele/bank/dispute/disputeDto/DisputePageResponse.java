package za.co.ntsumele.bank.dispute.disputeDto;

import java.util.List;

/**
 * class : DisputePageResponse
 * Author : Nyiko Chauke
 * 2026
 *
 * Contains one authorized page of case summaries and the matching server-side pagination totals.
 * Copies the result list so callers cannot mutate the response contents after construction.
 */
public record DisputePageResponse(
        List<DisputeSummaryResponse> content,
        long totalElements,
        int totalPages,
        int number,
        int size) {
    public DisputePageResponse {
        content = List.copyOf(content);
    }
}
