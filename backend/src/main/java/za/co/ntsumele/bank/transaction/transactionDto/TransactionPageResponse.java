package za.co.ntsumele.bank.transaction.transactionDto;

import java.util.List;

/**
 * class : TransactionPageResponse
 * Author : Nyiko Chauke
 * 2026
 *
 * Wraps transaction response rows with the page number, size and total counts
 * for server-filtered account activity.
 */
public record TransactionPageResponse(
        List<TransactionActivityResponse> content,
        long totalElements,
        int totalPages,
        int number,
        int size) {
}
