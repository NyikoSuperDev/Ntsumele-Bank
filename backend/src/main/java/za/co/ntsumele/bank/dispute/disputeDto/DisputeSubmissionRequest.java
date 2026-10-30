package za.co.ntsumele.bank.dispute.disputeDto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.ntsumele.bank.dispute.disputeModel.DisputeReason;

/**
 * class : DisputeSubmissionRequest
 * Author : Nyiko Chauke
 * 2026
 *
 * Carries only the transaction identifier, selected reason and claim description for a new dispute.
 * Financial values are omitted so the submission service obtains them from the ledger.
 */
public record DisputeSubmissionRequest(
        @NotBlank @Size(max = 80) String transactionId,
        @NotNull DisputeReason reason,
        @NotBlank @Size(min = 10, max = 2000) String description) {
}
