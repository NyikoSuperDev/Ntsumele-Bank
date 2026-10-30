package za.co.ntsumele.bank.transaction.transactionService;

import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.ntsumele.bank.account.accountRepository.AccountRepository;
import za.co.ntsumele.bank.failure.failureException.BankingRuleViolation;
import za.co.ntsumele.bank.transaction.transactionDto.TransactionActivityResponse;
import za.co.ntsumele.bank.transaction.transactionDto.TransactionPageResponse;
import za.co.ntsumele.bank.transaction.transactionMapper.TransactionResponseMapper;
import za.co.ntsumele.bank.transaction.transactionModel.LedgerTransaction;
import za.co.ntsumele.bank.transaction.transactionModel.TransactionSearchResult;
import za.co.ntsumele.bank.transaction.transactionModel.TransactionDisputeStatus;
import za.co.ntsumele.bank.transaction.transactionPolicy.TransactionEligibilityPolicy;
import za.co.ntsumele.bank.transaction.transactionRepository.LedgerTransactionRepository;

/**
 * class : TransactionInquiryService
 * Author : Nyiko Chauke
 * 2026
 *
 * Validates transaction search criteria and explicit account access, retrieves the authorized
 * page, and maps each ledger projection to a response with dispute eligibility.
 * It keeps payment status and derived dispute status independent.
 */

@Service
@Transactional(readOnly = true)
public class TransactionInquiryService {
    private static final Set<String> TRANSACTION_STATUSES = Set.of("BOOKED", "PENDING", "REVERSED");

    private final AccountRepository accountRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final TransactionEligibilityPolicy transactionEligibilityPolicy;

    public TransactionInquiryService(
            AccountRepository accountRepository,
            LedgerTransactionRepository ledgerTransactionRepository,
            TransactionEligibilityPolicy transactionEligibilityPolicy) {
        this.accountRepository = accountRepository;
        this.ledgerTransactionRepository = ledgerTransactionRepository;
        this.transactionEligibilityPolicy = transactionEligibilityPolicy;
    }

    public TransactionPageResponse getTransactions(
            String username, String accountId, int page, int size, String search, String status,
            String disputeStatus) {
        if (accountId != null && !accountId.isBlank()
                && !accountRepository.hasAccountAccess(username, accountId)) {
            throw BankingRuleViolation.notFound();
        }
        if (page < 0 || page > 10000 || size < 1 || size > 100 || search.length() > 120
                || (!status.isBlank() && !TRANSACTION_STATUSES.contains(status))) {
            throw BankingRuleViolation.badRequest("INVALID_REQUEST", "Check the request fields and try again.");
        }

        TransactionDisputeStatus disputeFilter = null;
        if (!disputeStatus.isBlank()) {
            try {
                disputeFilter = TransactionDisputeStatus.valueOf(disputeStatus);
            } catch (IllegalArgumentException exception) {
                throw BankingRuleViolation.badRequest("INVALID_REQUEST", "Check the request fields and try again.");
            }
        }

        TransactionSearchResult transactions = ledgerTransactionRepository.findAuthorizedTransactions(
                username, accountId, page, size, search, status, disputeFilter);
        return new TransactionPageResponse(
                transactions.content().stream().map(this::toResponse).toList(),
                transactions.totalElements(),
                transactions.totalPages(),
                transactions.number(),
                transactions.size());
    }

    public TransactionActivityResponse toResponse(LedgerTransaction transaction) {
        return TransactionResponseMapper.toResponse(transaction, transactionEligibilityPolicy.isDisputable(transaction));
    }
}
