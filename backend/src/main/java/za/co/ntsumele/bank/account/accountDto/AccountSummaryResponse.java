package za.co.ntsumele.bank.account.accountDto;

import java.math.BigDecimal;

/**
 * class : AccountSummaryResponse
 * Author : Nyiko Chauke
 * 2026
 *
 * Carries the account identifier, display name, masked number, currency and balance
 * returned by the customer account API.
 */
public record AccountSummaryResponse(
        String id,
        String name,
        String maskedNumber,
        String currency,
        BigDecimal balance) {
}
