package za.co.ntsumele.bank.dispute.disputeController;

import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.ntsumele.bank.dispute.disputeDto.DisputeCaseResponse;
import za.co.ntsumele.bank.dispute.disputeDto.DisputePageResponse;
import za.co.ntsumele.bank.dispute.disputeDto.DisputeCountsResponse;
import za.co.ntsumele.bank.dispute.disputeDto.DisputeStatusUpdateRequest;
import za.co.ntsumele.bank.dispute.disputeService.DisputeInquiryService;
import za.co.ntsumele.bank.dispute.disputeService.DisputeReviewService;

/**
 * class : DisputeReviewsController
 * Author : Nyiko Chauke
 * 2026
 *
 * Exposes staff endpoints for paged case summaries, aggregate counts, complete case details
 * and versioned status updates.
 * Delegates case inquiry and review decisions to the corresponding dispute services.
 */
@RestController
@RequestMapping("/api/staff/disputes")
public class DisputeReviewsController {

    private final DisputeInquiryService disputeInquiryService;
    private final DisputeReviewService disputeReviewService;

    public DisputeReviewsController(
            DisputeInquiryService disputeInquiryService, DisputeReviewService disputeReviewService) {
        this.disputeInquiryService = disputeInquiryService;
        this.disputeReviewService = disputeReviewService;
    }

    @GetMapping
    public DisputePageResponse getDisputes(
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return disputeInquiryService.getStaffDisputes(status, page, size);
    }

    @GetMapping("/summary")
    public DisputeCountsResponse getSummary() {
        return disputeInquiryService.getStaffCounts();
    }

    @GetMapping("/{id}")
    public DisputeCaseResponse getDispute(@PathVariable("id") String disputeId) {
        return disputeInquiryService.getStaffDispute(disputeId);
    }

    @PostMapping("/{id}/status")
    public DisputeCaseResponse updateStatus(
            Principal principal,
            @PathVariable("id") String disputeId,
            @Valid @RequestBody DisputeStatusUpdateRequest request) {
        return disputeReviewService.updateStatus(principal.getName(), disputeId, request);
    }
}
