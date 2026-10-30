package za.co.ntsumele.bank.dispute.disputeService;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import za.co.ntsumele.bank.audit.auditService.AuditTrailService;
import za.co.ntsumele.bank.dispute.disputeModel.DisputeCase;
import za.co.ntsumele.bank.dispute.disputeModel.DisputeHistoryEntry;
import za.co.ntsumele.bank.dispute.disputeModel.DisputeStatus;
import za.co.ntsumele.bank.dispute.disputeRepository.DisputeCaseRepository;
import za.co.ntsumele.bank.dispute.disputeRepository.DisputeHistoryRepository;
import za.co.ntsumele.bank.failure.failureException.BankingRuleViolation;

/**
 * class : DisputeLifecycleService
 * Author : Nyiko Chauke
 * 2026
 *
 * Internal case-state writer. Callers must already hold an application transaction.
 * Locks and version-checks cases, then records state, history and audit changes together.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
class DisputeLifecycleService {

    private final DisputeCaseRepository disputeCaseRepository;
    private final DisputeHistoryRepository disputeHistoryRepository;
    private final DisputeInquiryService disputeInquiryService;
    private final AuditTrailService auditTrailService;
    private final Clock clock;

    DisputeLifecycleService(
            DisputeCaseRepository disputeCaseRepository,
            DisputeHistoryRepository disputeHistoryRepository,
            DisputeInquiryService disputeInquiryService,
            AuditTrailService auditTrailService,
            Clock clock) {
        this.disputeCaseRepository = disputeCaseRepository;
        this.disputeHistoryRepository = disputeHistoryRepository;
        this.disputeInquiryService = disputeInquiryService;
        this.auditTrailService = auditTrailService;
        this.clock = clock;
    }

    DisputeCase lockCurrentVersion(String disputeId, Long expectedVersion) {
        if (!disputeCaseRepository.lockById(disputeId)) {
            throw BankingRuleViolation.notFound();
        }
        DisputeCase currentCase = disputeInquiryService.findCase(disputeId);
        if (expectedVersion == null || currentCase.version() != expectedVersion.longValue()) {
            throw BankingRuleViolation.conflict("VERSION_CONFLICT",
                    "This case changed. Refresh it before continuing.");
        }
        return currentCase;
    }

    void recordStatusChange(
            DisputeCase currentCase, DisputeStatus status, String note, String actor, String action) {
        Instant occurredAt = clock.instant();
        boolean resolved = status == DisputeStatus.RESOLVED;
        int updatedRecords = disputeCaseRepository.updateStatus(currentCase.id(), status,
                resolved ? null : currentCase.transactionId(), occurredAt, resolved ? note : null,
                currentCase.version());
        if (updatedRecords != 1) {
            throw BankingRuleViolation.conflict("VERSION_CONFLICT",
                    "This case changed. Refresh it before continuing.");
        }
        disputeHistoryRepository.insert(new DisputeHistoryEntry(UUID.randomUUID().toString(),
                currentCase.id(), currentCase.status(), status, actor, occurredAt, note));
        auditTrailService.recordAction(actor, action, currentCase.id(),
                "status: " + currentCase.status() + " -> " + status
                        + "; version: " + currentCase.version() + " -> " + (currentCase.version() + 1));
    }
}
