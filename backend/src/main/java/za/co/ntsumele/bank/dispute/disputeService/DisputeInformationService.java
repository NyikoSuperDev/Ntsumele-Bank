package za.co.ntsumele.bank.dispute.disputeService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.ntsumele.bank.account.accountRepository.AccountRepository;
import za.co.ntsumele.bank.dispute.disputeDto.DisputeCaseResponse;
import za.co.ntsumele.bank.dispute.disputeDto.DisputeInformationRequest;
import za.co.ntsumele.bank.dispute.disputeModel.DisputeCase;
import za.co.ntsumele.bank.dispute.disputeModel.DisputeStatus;
import za.co.ntsumele.bank.failure.failureException.BankingRuleViolation;

/**
 * class : DisputeInformationService
 * Author : Nyiko Chauke
 * 2026
 *
 * Accepts a customer response only when an authorized, version-matching case is awaiting information.
 * Records the response and returns the case to under review within the application transaction.
 */
@Service
@Transactional
public class DisputeInformationService {

    private final AccountRepository accountRepository;
    private final DisputeLifecycleService disputeLifecycleService;
    private final DisputeInquiryService disputeInquiryService;

    public DisputeInformationService(
            AccountRepository accountRepository,
            DisputeLifecycleService disputeLifecycleService,
            DisputeInquiryService disputeInquiryService) {
        this.accountRepository = accountRepository;
        this.disputeLifecycleService = disputeLifecycleService;
        this.disputeInquiryService = disputeInquiryService;
    }

    public DisputeCaseResponse provideInformation(
            String username, String disputeId, DisputeInformationRequest request) {
        if (!accountRepository.hasDisputeAccess(username, disputeId)) {
            throw BankingRuleViolation.notFound();
        }
        DisputeCase currentCase = disputeLifecycleService.lockCurrentVersion(disputeId, request.version());
        if (currentCase.status() != DisputeStatus.AWAITING_INFORMATION) {
            throw BankingRuleViolation.conflict("INVALID_TRANSITION", "This case is not awaiting information.");
        }
        disputeLifecycleService.recordStatusChange(currentCase, DisputeStatus.UNDER_REVIEW,
                request.message(), username, "INFORMATION_PROVIDED");
        return disputeInquiryService.toResponse(disputeInquiryService.findCase(disputeId));
    }
}
