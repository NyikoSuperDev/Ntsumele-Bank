package za.co.ntsumele.bank.account.accountController;

import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.ntsumele.bank.account.accountDto.AccountSummaryResponse;
import za.co.ntsumele.bank.account.accountService.AccountInquiryService;

/**
 * class : AccountsController
 * Author : Nyiko Chauke
 * 2026
 *
 * Exposes the customer account-list endpoint and uses the authenticated principal
 * to request authorized account summaries.
 */
@RestController
@RequestMapping("/api/accounts")
public class AccountsController {

    private final AccountInquiryService accountInquiryService;

    public AccountsController(AccountInquiryService accountInquiryService) {
        this.accountInquiryService = accountInquiryService;
    }

    @GetMapping
    public List<AccountSummaryResponse> getAccounts(Principal principal) {
        return accountInquiryService.getAuthorizedAccounts(principal.getName());
    }
}
