package za.co.ntsumele.bank.transaction.transactionModel;

import java.util.List;

/**
 * class : TransactionSearchResult
 * Author : Nyiko Chauke
 * 2026
 *
 * Contains transaction domain rows and pagination metadata returned by the repository.
 * Its constructor defensively copies the result list to prevent later modification.
 */
public record TransactionSearchResult(
        List<LedgerTransaction> content,
        long totalElements,
        int totalPages,
        int number,
        int size) {

    public TransactionSearchResult {
        content = List.copyOf(content);
    }
}
