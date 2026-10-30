package za.co.ntsumele.bank.account.accountService;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.ntsumele.bank.account.accountDto.AccountSummaryResponse;
import za.co.ntsumele.bank.account.accountMapper.AccountResponseMapper;
import za.co.ntsumele.bank.account.accountRepository.AccountRepository;

/**
 * class : AccountInquiryService
 * Author : Nyiko Chauke
 * 2026
 *
 * Retrieves the accounts authorized for a username and maps them to customer-facing
 * summaries within a read-only transaction.
 */
@Service
@Transactional(readOnly = true)
public class AccountInquiryService {
    private final AccountRepository accountRepository;

    public AccountInquiryService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public List<AccountSummaryResponse> getAuthorizedAccounts(String username) {
        return accountRepository.findAuthorizedAccounts(username).stream()
                .map(AccountResponseMapper::toResponse)
                .toList();
    }
}
