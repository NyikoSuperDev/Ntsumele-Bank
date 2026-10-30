package za.co.ntsumele.bank.dispute.disputeDto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * class : DisputeInformationRequest
 * Author : Nyiko Chauke
 * 2026
 *
 * Carries a customer response to a request for supporting information and the expected case version.
 * Validation constraints bound the message and require a non-negative version.
 */
public record DisputeInformationRequest(
        @NotBlank @Size(min = 5, max = 2000) String message,
        @NotNull @PositiveOrZero Long version) {
}
