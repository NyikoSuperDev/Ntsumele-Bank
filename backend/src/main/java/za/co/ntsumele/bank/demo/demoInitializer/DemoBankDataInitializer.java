package za.co.ntsumele.bank.demo.demoInitializer;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.ntsumele.bank.account.accountModel.BankAccount;
import za.co.ntsumele.bank.account.accountRepository.AccountRepository;
import za.co.ntsumele.bank.audit.auditService.AuditTrailService;
import za.co.ntsumele.bank.demo.demoRepository.DemoSeedRepository;
import za.co.ntsumele.bank.dispute.disputeModel.DisputeCase;
import za.co.ntsumele.bank.dispute.disputeModel.DisputeHistoryEntry;
import za.co.ntsumele.bank.dispute.disputeModel.DisputeReason;
import za.co.ntsumele.bank.dispute.disputeModel.DisputeStatus;
import za.co.ntsumele.bank.dispute.disputeRepository.DisputeCaseRepository;
import za.co.ntsumele.bank.dispute.disputeRepository.DisputeHistoryRepository;
import za.co.ntsumele.bank.identity.identityModel.BankIdentity;
import za.co.ntsumele.bank.identity.identityRepository.BankIdentityRepository;
import za.co.ntsumele.bank.transaction.transactionModel.LedgerTransaction;
import za.co.ntsumele.bank.transaction.transactionRepository.LedgerTransactionRepository;

/**
 * class : DemoBankDataInitializer
 * Author : Nyiko Chauke
 * 2026
 *
 * Seeds fictional users, accounts, ledger transactions, dispute cases and histories when the demo profile is active and no users exist.
 * Creates the data atomically under the shared initialization lock and preserves existing records on later startups.
 */
@Component
@Profile("demo")
public class DemoBankDataInitializer implements CommandLineRunner {

    private final DemoSeedRepository demoSeedRepository;
    private final BankIdentityRepository bankIdentityRepository;
    private final AccountRepository accountRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final DisputeCaseRepository disputeCaseRepository;
    private final DisputeHistoryRepository disputeHistoryRepository;
    private final AuditTrailService auditTrailService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public DemoBankDataInitializer(
            DemoSeedRepository demoSeedRepository,
            BankIdentityRepository bankIdentityRepository,
            AccountRepository accountRepository,
            LedgerTransactionRepository ledgerTransactionRepository,
            DisputeCaseRepository disputeCaseRepository,
            DisputeHistoryRepository disputeHistoryRepository,
            AuditTrailService auditTrailService,
            PasswordEncoder passwordEncoder,
            Clock clock) {
        this.demoSeedRepository = demoSeedRepository;
        this.bankIdentityRepository = bankIdentityRepository;
        this.accountRepository = accountRepository;
        this.ledgerTransactionRepository = ledgerTransactionRepository;
        this.disputeCaseRepository = disputeCaseRepository;
        this.disputeHistoryRepository = disputeHistoryRepository;
        this.auditTrailService = auditTrailService;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(String... arguments) {
        demoSeedRepository.lockInitialization();
        if (bankIdentityRepository.count() > 0) {
            return;
        }
        createUser("amy", "Amy Mokoena", "Ntsumele!2026", "CUSTOMER");
        createUser("ben", "Ben Dlamini", "Ntsumele!2026", "CUSTOMER");
        createUser("reviewer", "Disputes team", "Review!2026", "STAFF");

        createAccount("acc-everyday", "Everyday account", "•••• 4829", "24850.75", "amy");
        createAccount("acc-savings", "Savings account", "•••• 9012", "85000.00", "amy");
        createAccount("acc-ben", "Everyday account", "•••• 7351", "12500.00", "ben");

        createTransaction("tx-1001", "acc-everyday", "Woolworths Food", "WOOL-784921", "-684.50", 0, "BOOKED", "Groceries", null);
        createTransaction("tx-1002", "acc-everyday", "Uber South Africa", "UBER-296841", "-156.00", 0, "BOOKED", "Transport", null);
        createTransaction("tx-1003", "acc-everyday", "Netflix", "NFLX-884210", "-199.00", 1, "BOOKED", "Entertainment", null);
        createTransaction("tx-1004", "acc-everyday", "Takealot", "TKLT-658903", "-1299.00", 5, "BOOKED", "Shopping", null);
        createTransaction("tx-1005", "acc-everyday", "Checkers Sixty60", "CHK-521884", "-542.80", 3, "BOOKED", "Groceries", null);
        createTransaction("tx-1006", "acc-everyday", "Salary • Acme Studio", "SAL-202610", "32000.00", 4, "BOOKED", "Income", null);
        createTransaction("tx-1007", "acc-everyday", "Shell Rosebank", "SHEL-431982", "-850.00", 4, "PENDING", "Transport", null);
        createTransaction("tx-1008", "acc-everyday", "Spotify", "SPOT-987113", "-64.99", 9, "BOOKED", "Entertainment", null);
        createTransaction("tx-1009", "acc-everyday", "Online Electronics", "ELEC-339810", "-2450.00", 12, "BOOKED", "Shopping", null);
        createTransaction("tx-1010", "acc-everyday", "Vida e Caffè", "VIDA-773982", "-48.00", 7, "BOOKED", "Dining", null);
        createTransaction("tx-1011", "acc-everyday", "Hotel reservation", "HOTEL-231998", "-1800.00", 8, "REVERSED", "Travel", null);
        createTransaction("tx-1012", "acc-everyday", "Hotel reversal", "REV-231998", "1800.00", 7, "BOOKED", "Travel", "tx-1011");
        createTransaction("tx-1013", "acc-savings", "Monthly savings transfer", "SAVE-100829", "2500.00", 8, "BOOKED", "Transfer", null);
        createTransaction("tx-1014", "acc-everyday", "City Power", "CITY-663108", "-650.00", 9, "BOOKED", "Utilities", null);
        createTransaction("tx-2001", "acc-ben", "Private purchase", "BEN-PRIVATE-1", "-999.00", 1, "BOOKED", "Shopping", null);

        createDispute("case-1001", "NB-2026-10482", "tx-1004", DisputeReason.GOODS_NOT_RECEIVED,
                "My order has not arrived after the estimated delivery date.", DisputeStatus.AWAITING_INFORMATION, 4);
        createDispute("case-1002", "NB-2026-10396", "tx-1008", DisputeReason.DUPLICATE,
                "I was charged twice for the same monthly subscription.", DisputeStatus.UNDER_REVIEW, 6);
        createDispute("case-1003", "NB-2026-10214", "tx-1009", DisputeReason.UNRECOGNIZED,
                "I do not recognize this purchase on my card statement.", DisputeStatus.RESOLVED, 9);
        auditTrailService.recordAction("system", "DEMO_INITIALIZED", null, "Synthetic demonstration data initialized");
    }

    private void createUser(String username, String displayName, String password, String role) {
        bankIdentityRepository.insert(new BankIdentity(username, displayName, passwordEncoder.encode(password), role));
    }

    private void createAccount(
            String accountId, String name, String maskedNumber, String balance, String username) {
        accountRepository.insert(new BankAccount(accountId, name, maskedNumber, "ZAR", new BigDecimal(balance)));
        accountRepository.grantAccess(username, accountId);
    }

    private void createTransaction(
            String transactionId, String accountId, String merchant, String reference, String amount,
            int daysAgo, String status, String category, String reversalOf) {
        Instant observedAt = clock.instant();
        Instant bookedAt = observedAt.minus(daysAgo, ChronoUnit.DAYS).minus(2, ChronoUnit.HOURS);
        ledgerTransactionRepository.insert(new LedgerTransaction(
                transactionId, accountId, merchant, reference, new BigDecimal(amount), "ZAR", bookedAt,
                status, category, observedAt, reversalOf, null, null, false));
    }

    private void createDispute(
            String disputeId, String caseReference, String transactionId, DisputeReason reason,
            String description, DisputeStatus status, int daysAgo) {
        Instant createdAt = clock.instant().minus(daysAgo, ChronoUnit.DAYS);
        long version = switch (status) {
            case UNDER_REVIEW -> 1;
            case AWAITING_INFORMATION -> 2;
            default -> 3;
        };
        String resolution = status == DisputeStatus.RESOLVED
                ? "Investigation completed. The merchant accepted the dispute. Any adjustment is processed separately by the banking ledger."
                : null;
        disputeCaseRepository.insert(new DisputeCase(
                disputeId, caseReference, transactionId, "amy", reason, description, status,
                createdAt, createdAt.plus(1, ChronoUnit.DAYS), resolution, version));
        recordHistory(disputeId, null, DisputeStatus.SUBMITTED, "amy", createdAt, description);
        recordHistory(disputeId, DisputeStatus.SUBMITTED, DisputeStatus.UNDER_REVIEW, "reviewer",
                createdAt.plus(3, ChronoUnit.HOURS), "Your case has been assigned to the disputes team.");
        if (status == DisputeStatus.AWAITING_INFORMATION) {
            recordHistory(disputeId, DisputeStatus.UNDER_REVIEW, status, "reviewer",
                    createdAt.plus(1, ChronoUnit.DAYS),
                    "Please share the expected delivery date and your latest communication with the merchant.");
        }
        if (status == DisputeStatus.RESOLVED) {
            recordHistory(disputeId, DisputeStatus.UNDER_REVIEW, DisputeStatus.AWAITING_INFORMATION, "reviewer",
                    createdAt.plus(5, ChronoUnit.HOURS), "Please confirm whether this card was in your possession.");
            recordHistory(disputeId, DisputeStatus.AWAITING_INFORMATION, status, "reviewer",
                    createdAt.plus(1, ChronoUnit.DAYS), resolution);
        }
    }

    private void recordHistory(
            String disputeId, DisputeStatus fromStatus, DisputeStatus toStatus, String actor,
            Instant occurredAt, String note) {
        disputeHistoryRepository.insert(new DisputeHistoryEntry(
                UUID.randomUUID().toString(), disputeId, fromStatus, toStatus, actor, occurredAt, note));
        auditTrailService.recordActionAt(actor, "DEMO_STATUS_HISTORY", disputeId,
                "status: " + fromStatus + " -> " + toStatus, occurredAt);
    }
}
