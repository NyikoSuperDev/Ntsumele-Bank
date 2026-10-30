package za.co.ntsumele.bank.dispute.disputeController;

import jakarta.validation.Valid;
import java.net.URI;
import java.security.Principal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.ntsumele.bank.dispute.disputeDto.DisputeCaseResponse;
import za.co.ntsumele.bank.dispute.disputeDto.DisputePageResponse;
import za.co.ntsumele.bank.dispute.disputeDto.DisputeCountsResponse;
import za.co.ntsumele.bank.dispute.disputeDto.DisputeInformationRequest;
import za.co.ntsumele.bank.dispute.disputeDto.DisputeSubmissionRequest;
import za.co.ntsumele.bank.dispute.disputeService.DisputeInformationService;
import za.co.ntsumele.bank.dispute.disputeService.DisputeInquiryService;
import za.co.ntsumele.bank.dispute.disputeService.DisputeSubmissionService;

/**
 * class : DisputesController
 * Author : Nyiko Chauke
 * 2026
 *
 * Exposes customer endpoints for paged case summaries, workspace counts, individual case details,
 * dispute submission and requested-information responses.
 * Passes the authenticated principal and submission idempotency key to the relevant use case.
 */
@RestController
@RequestMapping("/api/disputes")
public class DisputesController {

    private final DisputeInquiryService disputeInquiryService;
    private final DisputeSubmissionService disputeSubmissionService;
    private final DisputeInformationService disputeInformationService;

    public DisputesController(
            DisputeInquiryService disputeInquiryService,
            DisputeSubmissionService disputeSubmissionService,
            DisputeInformationService disputeInformationService) {
        this.disputeInquiryService = disputeInquiryService;
        this.disputeSubmissionService = disputeSubmissionService;
        this.disputeInformationService = disputeInformationService;
    }

    @GetMapping
    public DisputePageResponse getDisputes(
            Principal principal, @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return disputeInquiryService.getCustomerDisputes(principal.getName(), status, page, size);
    }

    @GetMapping("/summary")
    public DisputeCountsResponse getSummary(Principal principal) {
        return disputeInquiryService.getCustomerCounts(principal.getName());
    }

    @GetMapping("/{id}")
    public DisputeCaseResponse getDispute(Principal principal, @PathVariable("id") String disputeId) {
        return disputeInquiryService.getCustomerDispute(principal.getName(), disputeId);
    }

    @PostMapping
    public ResponseEntity<DisputeCaseResponse> submitDispute(
            Principal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody DisputeSubmissionRequest request) {
        DisputeCaseResponse dispute = disputeSubmissionService.submitDispute(
                principal.getName(), idempotencyKey, request);
        return ResponseEntity.created(URI.create("/api/disputes/" + dispute.id())).body(dispute);
    }

    @PostMapping("/{id}/information")
    public DisputeCaseResponse provideInformation(
            Principal principal,
            @PathVariable("id") String disputeId,
            @Valid @RequestBody DisputeInformationRequest request) {
        return disputeInformationService.provideInformation(principal.getName(), disputeId, request);
    }
}
