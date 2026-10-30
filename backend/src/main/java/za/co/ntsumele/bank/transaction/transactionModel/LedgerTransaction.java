package za.co.ntsumele.bank.transaction.transactionModel;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * class : LedgerTransaction
 * Author : Nyiko Chauke
 * 2026
 *
 * Holds immutable ledger details with observed freshness, reversal linkage and case identifiers.
 * It derives a dispute classification from case links without changing the ledger payment status.
 */
public record LedgerTransaction(
        String id,
        String accountId,
        String merchant,
        String reference,
        BigDecimal amount,
        String currency,
        Instant bookedAt,
        String status,
        String category,
        Instant observedAt,
        String reversalOf,
        String activeDisputeId,
        String latestDisputeId,
        boolean hasReversal) {

    public TransactionDisputeStatus disputeStatus() {
        if (activeDisputeId != null) {
            return TransactionDisputeStatus.DISPUTED;
        }
        return latestDisputeId == null
                ? TransactionDisputeStatus.NOT_DISPUTED
                : TransactionDisputeStatus.DISPUTE_RESOLVED;
    }
}
