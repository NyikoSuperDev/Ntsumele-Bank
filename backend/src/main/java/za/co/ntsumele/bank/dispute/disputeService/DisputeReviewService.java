package za.co.ntsumele.bank.dispute.disputeService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.ntsumele.bank.dispute.disputeDto.DisputeCaseResponse;
import za.co.ntsumele.bank.dispute.disputeDto.DisputeStatusUpdateRequest;
import za.co.ntsumele.bank.dispute.disputeModel.DisputeCase;
import za.co.ntsumele.bank.dispute.disputeModel.DisputeStatus;
import za.co.ntsumele.bank.failure.failureException.BankingRuleViolation;

/**
 * class : DisputeReviewService
 * Author : Nyiko Chauke
 * 2026
 *
 * Applies permitted staff review decisions to a locked, version-matching case.
 * Uses the lifecycle writer to record state, resolution notes, history and audit events,
 * and rejects further transitions for resolved cases.
 */
@Service
@Transactional
public class DisputeReviewService {

    private final DisputeLifecycleService disputeLifecycleService;
    private final DisputeInquiryService disputeInquiryService;

    public DisputeReviewService(
            DisputeLifecycleService disputeLifecycleService, DisputeInquiryService disputeInquiryService) {
        this.disputeLifecycleService = disputeLifecycleService;
        this.disputeInquiryService = disputeInquiryService;
    }

    public DisputeCaseResponse updateStatus(
            String actor, String disputeId, DisputeStatusUpdateRequest request) {
        DisputeCase currentCase = disputeLifecycleService.lockCurrentVersion(disputeId, request.version());
        boolean validTransition = switch (currentCase.status()) {
            case SUBMITTED -> request.status() == DisputeStatus.UNDER_REVIEW;
            case UNDER_REVIEW -> request.status() == DisputeStatus.AWAITING_INFORMATION
                    || request.status() == DisputeStatus.RESOLVED;
            case AWAITING_INFORMATION -> request.status() == DisputeStatus.UNDER_REVIEW
                    || request.status() == DisputeStatus.RESOLVED;
            case RESOLVED -> false;
        };
        if (!validTransition) {
            throw BankingRuleViolation.conflict("INVALID_TRANSITION", "This status transition is not allowed.");
        }
        disputeLifecycleService.recordStatusChange(
                currentCase, request.status(), request.note(), actor, "STATUS_CHANGED");
        return disputeInquiryService.toResponse(disputeInquiryService.findCase(disputeId));
    }
}
