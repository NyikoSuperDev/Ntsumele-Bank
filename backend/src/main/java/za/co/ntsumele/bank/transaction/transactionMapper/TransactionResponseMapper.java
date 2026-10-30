package za.co.ntsumele.bank.transaction.transactionMapper;

import za.co.ntsumele.bank.transaction.transactionDto.TransactionActivityResponse;
import za.co.ntsumele.bank.transaction.transactionModel.LedgerTransaction;

/**
 * class : TransactionResponseMapper
 * Author : Nyiko Chauke
 * 2026
 *
 * Maps an immutable ledger projection and its evaluated eligibility into the transaction
 * API response, including active and historical dispute links.
 */
public final class TransactionResponseMapper {
    private TransactionResponseMapper() {
    }

    public static TransactionActivityResponse toResponse(LedgerTransaction transaction, boolean disputable) {
        return new TransactionActivityResponse(
                transaction.id(),
                transaction.accountId(),
                transaction.merchant(),
                transaction.reference(),
                transaction.amount(),
                transaction.currency(),
                transaction.bookedAt(),
                transaction.status(),
                transaction.category(),
                disputable,
                transaction.activeDisputeId(),
                transaction.observedAt(),
                transaction.disputeStatus(),
                transaction.latestDisputeId());
    }
}
