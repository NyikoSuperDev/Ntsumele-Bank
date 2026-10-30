package za.co.ntsumele.bank.transaction.transactionDto;

import java.math.BigDecimal;
import java.time.Instant;
import za.co.ntsumele.bank.transaction.transactionModel.TransactionDisputeStatus;

/**
 * class : TransactionActivityResponse
 * Author : Nyiko Chauke
 * 2026
 *
 * Carries transaction details, ledger freshness, dispute eligibility and case links
 * to the client. Payment status and derived dispute status are separate fields.
 */
public record TransactionActivityResponse(
        String id,
        String accountId,
        String merchant,
        String reference,
        BigDecimal amount,
        String currency,
        Instant bookedAt,
        String status,
        String category,
        boolean disputable,
        String activeDisputeId,
        Instant observedAt,
        TransactionDisputeStatus disputeStatus,
        String latestDisputeId) {
}
