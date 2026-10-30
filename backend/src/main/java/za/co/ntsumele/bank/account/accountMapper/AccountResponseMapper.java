package za.co.ntsumele.bank.account.accountMapper;

import za.co.ntsumele.bank.account.accountDto.AccountSummaryResponse;
import za.co.ntsumele.bank.account.accountModel.BankAccount;

/**
 * class : AccountResponseMapper
 * Author : Nyiko Chauke
 * 2026
 *
 * Converts an account domain value into the account summary returned to the client.
 */
public final class AccountResponseMapper {
    private AccountResponseMapper() {
    }

    public static AccountSummaryResponse toResponse(BankAccount account) {
        return new AccountSummaryResponse(
                account.id(), account.name(), account.maskedNumber(), account.currency(), account.balance());
    }
}
