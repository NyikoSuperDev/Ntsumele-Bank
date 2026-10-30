package za.co.ntsumele.bank.dispute.disputeService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.ntsumele.bank.account.accountRepository.AccountRepository;
import za.co.ntsumele.bank.audit.auditService.AuditTrailService;
import za.co.ntsumele.bank.dispute.disputeDto.DisputeCaseResponse;
import za.co.ntsumele.bank.dispute.disputeDto.DisputeSubmissionRequest;
import za.co.ntsumele.bank.dispute.disputeModel.DisputeCase;
import za.co.ntsumele.bank.dispute.disputeModel.DisputeHistoryEntry;
import za.co.ntsumele.bank.dispute.disputeModel.DisputeIdempotencyRecord;
import za.co.ntsumele.bank.dispute.disputeModel.DisputeStatus;
import za.co.ntsumele.bank.dispute.disputeRepository.DisputeCaseRepository;
import za.co.ntsumele.bank.dispute.disputeRepository.DisputeHistoryRepository;
import za.co.ntsumele.bank.dispute.disputeRepository.DisputeIdempotencyRepository;
import za.co.ntsumele.bank.failure.failureException.BankingRuleViolation;
import za.co.ntsumele.bank.identity.identityRepository.BankIdentityRepository;
import za.co.ntsumele.bank.transaction.transactionModel.LedgerTransaction;
import za.co.ntsumele.bank.transaction.transactionPolicy.TransactionEligibilityPolicy;
import za.co.ntsumele.bank.transaction.transactionRepository.LedgerTransactionRepository;

/**
 * class : DisputeSubmissionService
 * Author : Nyiko Chauke
 * 2026
 *
 * Submits a customer claim atomically with account-access and ledger-eligibility checks,
 * principal-scoped idempotency and consistent row locking. Creates the case, initial history,
 * audit event and replay record while leaving ledger financial values unchanged.
 */
@Service
@Transactional
public class DisputeSubmissionService {

    private final AccountRepository accountRepository;
    private final BankIdentityRepository bankIdentityRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final DisputeCaseRepository disputeCaseRepository;
    private final DisputeHistoryRepository disputeHistoryRepository;
    private final DisputeIdempotencyRepository disputeIdempotencyRepository;
    private final TransactionEligibilityPolicy transactionEligibilityPolicy;
    private final DisputeInquiryService disputeInquiryService;
    private final AuditTrailService auditTrailService;
    private final Clock clock;

    public DisputeSubmissionService(
            AccountRepository accountRepository,
            BankIdentityRepository bankIdentityRepository,
            LedgerTransactionRepository ledgerTransactionRepository,
            DisputeCaseRepository disputeCaseRepository,
            DisputeHistoryRepository disputeHistoryRepository,
            DisputeIdempotencyRepository disputeIdempotencyRepository,
            TransactionEligibilityPolicy transactionEligibilityPolicy,
            DisputeInquiryService disputeInquiryService,
            AuditTrailService auditTrailService,
            Clock clock) {
        this.accountRepository = accountRepository;
        this.bankIdentityRepository = bankIdentityRepository;
        this.ledgerTransactionRepository = ledgerTransactionRepository;
        this.disputeCaseRepository = disputeCaseRepository;
        this.disputeHistoryRepository = disputeHistoryRepository;
        this.disputeIdempotencyRepository = disputeIdempotencyRepository;
        this.transactionEligibilityPolicy = transactionEligibilityPolicy;
        this.disputeInquiryService = disputeInquiryService;
        this.auditTrailService = auditTrailService;
        this.clock = clock;
    }

    public DisputeCaseResponse submitDispute(
            String username, String requestKey, DisputeSubmissionRequest request) {
        if (requestKey == null || !requestKey.matches("[A-Za-z0-9._:-]{8,100}")) {
            throw BankingRuleViolation.badRequest("IDEMPOTENCY_KEY_REQUIRED",
                    "Send an Idempotency-Key of 8 to 100 safe characters.");
        }
        // Principal first, ledger second: maintain the same lock order across every submission.
        if (!bankIdentityRepository.lockByUsername(username)) {
            throw BankingRuleViolation.notFound();
        }
        if (!accountRepository.hasTransactionAccess(username, request.transactionId())) {
            throw BankingRuleViolation.notFound();
        }
        String payloadHash = hashPayload(request);
        DisputeIdempotencyRecord previous = disputeIdempotencyRepository
                .findByUsernameAndRequestKey(username, requestKey).orElse(null);
        if (previous != null) {
            if (!payloadHash.equals(previous.payloadHash())) {
                throw BankingRuleViolation.conflict("IDEMPOTENCY_CONFLICT",
                        "This key has already been used for a different request.");
            }
            return disputeInquiryService.toResponse(disputeInquiryService.findCase(previous.disputeId()));
        }
        if (!ledgerTransactionRepository.lockById(request.transactionId())) {
            throw BankingRuleViolation.notFound();
        }
        LedgerTransaction transaction = ledgerTransactionRepository.findById(request.transactionId())
                .orElseThrow(BankingRuleViolation::notFound);
        if (transaction.activeDisputeId() != null) {
            throw BankingRuleViolation.conflict("ACTIVE_DISPUTE_EXISTS",
                    "This transaction already has an active dispute.");
        }
        if (!transactionEligibilityPolicy.isDisputable(transaction)) {
            throw BankingRuleViolation.conflict("TRANSACTION_NOT_DISPUTABLE",
                    "Only fresh, booked debit transactions without a reversal can be disputed.");
        }

        String disputeId = UUID.randomUUID().toString();
        String caseReference = String.format(Locale.ROOT, "NB-%d-%012d",
                LocalDate.now(clock).getYear(), disputeCaseRepository.nextReferenceNumber());
        Instant occurredAt = clock.instant();
        DisputeCase disputeCase = new DisputeCase(disputeId, caseReference, transaction.id(), username,
                request.reason(), request.description(), DisputeStatus.SUBMITTED, occurredAt, occurredAt, null, 0);
        disputeCaseRepository.insert(disputeCase);
        disputeHistoryRepository.insert(new DisputeHistoryEntry(UUID.randomUUID().toString(), disputeId,
                null, DisputeStatus.SUBMITTED, username, occurredAt, request.description()));
        auditTrailService.recordAction(username, "DISPUTE_SUBMITTED", disputeId,
                "status: null -> SUBMITTED; transaction: " + transaction.id());
        disputeIdempotencyRepository.insert(new DisputeIdempotencyRecord(
                username, requestKey, payloadHash, disputeId, occurredAt));
        return disputeInquiryService.toResponse(disputeInquiryService.findCase(disputeId));
    }

    private static String hashPayload(DisputeSubmissionRequest request) {
        // Preserve existing persisted hashes when the application is upgraded.
        String payload = request.transactionId() + "\n" + request.reason() + "\n" + request.description();
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
