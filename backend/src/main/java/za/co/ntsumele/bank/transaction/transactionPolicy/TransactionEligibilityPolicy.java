package za.co.ntsumele.bank.transaction.transactionPolicy;

import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import za.co.ntsumele.bank.transaction.transactionModel.LedgerTransaction;

/**
 * class : TransactionEligibilityPolicy
 * Author : Nyiko Chauke
 * 2026
 *
 * Decides whether a trusted ledger projection is eligible for a new dispute.
 * It requires a fresh booked debit, no active case and no linked reversal.
 */
@Component
public class TransactionEligibilityPolicy {
    private final Clock clock;
    private final Duration maximumLedgerAge;

    public TransactionEligibilityPolicy(
            Clock clock,
            @Value("${app.ledger.max-age:24h}") Duration maximumLedgerAge) {
        this.clock = clock;
        this.maximumLedgerAge = maximumLedgerAge;
    }

    public boolean isDisputable(LedgerTransaction transaction) {
        return "BOOKED".equals(transaction.status())
                && transaction.amount().signum() < 0
                && transaction.activeDisputeId() == null
                && transaction.observedAt().plus(maximumLedgerAge).isAfter(clock.instant())
                && !transaction.hasReversal();
    }
}
