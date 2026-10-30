package za.co.ntsumele.bank.transaction.transactionController;

import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.ntsumele.bank.transaction.transactionDto.TransactionPageResponse;
import za.co.ntsumele.bank.transaction.transactionService.TransactionInquiryService;

/**
 * class : TransactionsController
 * Author : Nyiko Chauke
 * 2026
 *
 * Exposes paged transaction searches for the authenticated customer, accepting account,
 * merchant/reference, payment-status and dispute-status filters.
 */
@RestController
@RequestMapping("/api/transactions")
public class TransactionsController {

    private final TransactionInquiryService transactionInquiryService;

    public TransactionsController(TransactionInquiryService transactionInquiryService) {
        this.transactionInquiryService = transactionInquiryService;
    }

    @GetMapping
    public TransactionPageResponse getTransactions(
            Principal principal,
            @RequestParam(required = false) String accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "") String disputeStatus) {
        return transactionInquiryService.getTransactions(
                principal.getName(), accountId, page, size, search, status, disputeStatus);
    }
}
