package za.co.ntsumele.bank.account.accountModel;

import java.math.BigDecimal;

/**
 * class : BankAccount
 * Author : Nyiko Chauke
 * 2026
 *
 * Represents an account's identity, masked display number, currency and balance
 * as an immutable domain value.
 */
public record BankAccount(
        String id,
        String name,
        String maskedNumber,
        String currency,
        BigDecimal balance) {
}
