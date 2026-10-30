package za.co.ntsumele.bank.dispute.disputeDto;

import java.time.Instant;
import java.util.List;
import za.co.ntsumele.bank.dispute.disputeModel.DisputeReason;
import za.co.ntsumele.bank.dispute.disputeModel.DisputeStatus;
import za.co.ntsumele.bank.transaction.transactionDto.TransactionActivityResponse;

/**
 * class : DisputeCaseResponse
 * Author : Nyiko Chauke
 * 2026
 *
 * Returns a dispute case with its current state, linked transaction, resolution, concurrency version
 * and ordered activity history for the customer or staff workspace.
 */
public record DisputeCaseResponse(
        String id,
        String caseReference,
        TransactionActivityResponse transaction,
        DisputeReason reason,
        String description,
        DisputeStatus status,
        Instant createdAt,
        Instant updatedAt,
        String resolution,
        long version,
        List<DisputeHistoryEventResponse> history) {
}
