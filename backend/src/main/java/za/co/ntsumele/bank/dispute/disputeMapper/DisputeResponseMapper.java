package za.co.ntsumele.bank.dispute.disputeMapper;

import java.util.List;
import za.co.ntsumele.bank.dispute.disputeDto.DisputeCaseResponse;
import za.co.ntsumele.bank.dispute.disputeDto.DisputeHistoryEventResponse;
import za.co.ntsumele.bank.dispute.disputeModel.DisputeCase;
import za.co.ntsumele.bank.dispute.disputeModel.DisputeHistoryEntry;
import za.co.ntsumele.bank.transaction.transactionDto.TransactionActivityResponse;

/**
 * class : DisputeResponseMapper
 * Author : Nyiko Chauke
 * 2026
 *
 * Builds case and history API responses from stored case records, a transaction view and history entries.
 * Converts history status enums to response names without accessing the database.
 */
public final class DisputeResponseMapper {
    private DisputeResponseMapper() {
    }

    public static DisputeCaseResponse toResponse(
            DisputeCase disputeCase,
            TransactionActivityResponse transaction,
            List<DisputeHistoryEntry> history) {
        return new DisputeCaseResponse(
                disputeCase.id(),
                disputeCase.caseReference(),
                transaction,
                disputeCase.reason(),
                disputeCase.description(),
                disputeCase.status(),
                disputeCase.createdAt(),
                disputeCase.updatedAt(),
                disputeCase.resolution(),
                disputeCase.version(),
                history.stream().map(DisputeResponseMapper::toHistoryResponse).toList());
    }

    private static DisputeHistoryEventResponse toHistoryResponse(DisputeHistoryEntry entry) {
        return new DisputeHistoryEventResponse(
                entry.id(),
                entry.fromStatus() == null ? null : entry.fromStatus().name(),
                entry.toStatus().name(),
                entry.actor(),
                entry.occurredAt(),
                entry.note());
    }
}
