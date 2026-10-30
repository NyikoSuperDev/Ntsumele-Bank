package za.co.ntsumele.bank.dispute.disputeDto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import za.co.ntsumele.bank.dispute.disputeModel.DisputeStatus;

/**
 * class : DisputeStatusUpdateRequest
 * Author : Nyiko Chauke
 * 2026
 *
 * Carries a staff review decision, its explanatory note and the expected case version.
 * Validation constraints require the status and note, bound the note and reject negative versions.
 */
public record DisputeStatusUpdateRequest(
        @NotNull DisputeStatus status,
        @NotBlank @Size(min = 5, max = 2000) String note,
        @NotNull @PositiveOrZero Long version) {
}
