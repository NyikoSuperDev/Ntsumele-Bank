package za.co.ntsumele.bank.dispute.disputeDto;

import java.time.Instant;
import za.co.ntsumele.bank.dispute.disputeModel.DisputeReason;
import za.co.ntsumele.bank.dispute.disputeModel.DisputeStatus;
import za.co.ntsumele.bank.transaction.transactionDto.TransactionActivityResponse;

/**
 * class : DisputeSummaryResponse
 * Author : Nyiko Chauke
 * 2026
 *
 * Returns a bounded list-row view of a case with its transaction and activity count.
 * Case descriptions, resolution text and complete timelines are loaded only from the case-detail endpoint.
 */
public record DisputeSummaryResponse(
        String id,
        String caseReference,
        TransactionActivityResponse transaction,
        DisputeReason reason,
        DisputeStatus status,
        Instant createdAt,
        Instant updatedAt,
        long version,
        long historyCount) {
}
