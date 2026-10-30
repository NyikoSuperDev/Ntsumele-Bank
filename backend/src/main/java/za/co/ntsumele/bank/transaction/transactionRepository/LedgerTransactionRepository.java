package za.co.ntsumele.bank.transaction.transactionRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.ntsumele.bank.transaction.transactionModel.LedgerTransaction;
import za.co.ntsumele.bank.transaction.transactionModel.TransactionSearchResult;
import za.co.ntsumele.bank.transaction.transactionModel.TransactionDisputeStatus;

/**
 * class : LedgerTransactionRepository
 * Author : Nyiko Chauke
 * 2026
 *
 * Queries account-authorized transaction pages and derives case/reversal linkage using
 * PostgreSQL, sharing filters between rows and totals. It can lock a ledger row for dispute
 * creation and insert trusted snapshots without exposing a financial update method.
 */
@Repository
public class LedgerTransactionRepository {
    private static final String TRANSACTION_COLUMNS = """
            SELECT transaction_record.*,
                (SELECT dispute.id FROM dispute_case dispute
                 WHERE dispute.active_transaction_id = transaction_record.id) AS active_dispute_id,
                (SELECT dispute.id FROM dispute_case dispute
                 WHERE dispute.transaction_id = transaction_record.id
                 ORDER BY CASE WHEN dispute.active_transaction_id IS NOT NULL THEN 0 ELSE 1 END,
                          dispute.created_at DESC, dispute.id DESC LIMIT 1) AS latest_dispute_id,
                EXISTS(SELECT 1 FROM ledger_transaction reversal
                       WHERE reversal.reversal_of = transaction_record.id) AS has_reversal
            """;

    private final JdbcTemplate jdbcTemplate;

    public LedgerTransactionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public TransactionSearchResult findAuthorizedTransactions(
            String username, String accountId, int page, int size, String search, String status,
            TransactionDisputeStatus disputeStatus) {
        StringBuilder query = new StringBuilder("""
                 FROM ledger_transaction transaction_record
                 JOIN account_access entitlement ON entitlement.account_id = transaction_record.account_id
                 WHERE entitlement.username = ?
                """);
        List<Object> parameters = new ArrayList<>();
        parameters.add(username);

        if (accountId != null && !accountId.isBlank()) {
            query.append(" AND transaction_record.account_id = ?");
            parameters.add(accountId);
        }
        if (!status.isBlank()) {
            query.append(" AND transaction_record.status = ?");
            parameters.add(status);
        }
        if (disputeStatus != null) {
            query.append(switch (disputeStatus) {
                case NOT_DISPUTED -> """
                         AND NOT EXISTS (SELECT 1 FROM dispute_case dispute
                                         WHERE dispute.transaction_id = transaction_record.id)
                        """;
                case DISPUTED -> """
                         AND EXISTS (SELECT 1 FROM dispute_case dispute
                                     WHERE dispute.active_transaction_id = transaction_record.id)
                        """;
                case DISPUTE_RESOLVED -> """
                         AND EXISTS (SELECT 1 FROM dispute_case dispute
                                     WHERE dispute.transaction_id = transaction_record.id)
                         AND NOT EXISTS (SELECT 1 FROM dispute_case dispute
                                         WHERE dispute.active_transaction_id = transaction_record.id)
                        """;
            });
        }
        if (!search.isBlank()) {
            query.append("""
                     AND (LOWER(transaction_record.merchant) LIKE ? ESCAPE '!'
                          OR LOWER(transaction_record.provider_reference) LIKE ? ESCAPE '!')
                    """);
            String escapedSearch = "%" + search.toLowerCase(Locale.ROOT)
                    .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            parameters.add(escapedSearch);
            parameters.add(escapedSearch);
        }

        long totalElements = jdbcTemplate.queryForObject(
                "SELECT COUNT(*)" + query, Long.class, parameters.toArray());
        parameters.add(size);
        parameters.add((long) page * size);
        List<LedgerTransaction> transactions = jdbcTemplate.query(
                TRANSACTION_COLUMNS + query
                        + " ORDER BY transaction_record.booked_at DESC, transaction_record.id LIMIT ? OFFSET ?",
                (resultSet, rowNumber) -> mapTransaction(resultSet), parameters.toArray());
        int totalPages = (int) ((totalElements + size - 1) / size);
        return new TransactionSearchResult(transactions, totalElements, totalPages, page, size);
    }

    public Optional<LedgerTransaction> findById(String transactionId) {
        return jdbcTemplate.query(
                TRANSACTION_COLUMNS + " FROM ledger_transaction transaction_record WHERE transaction_record.id = ?",
                (resultSet, rowNumber) -> mapTransaction(resultSet), transactionId).stream().findFirst();
    }

    public List<LedgerTransaction> findAllByIds(Collection<String> transactionIds) {
        List<String> identifiers = List.copyOf(new LinkedHashSet<>(transactionIds));
        if (identifiers.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(identifiers.size(), "?"));
        return jdbcTemplate.query(TRANSACTION_COLUMNS
                        + " FROM ledger_transaction transaction_record WHERE transaction_record.id IN (" + placeholders + ")",
                (resultSet, rowNumber) -> mapTransaction(resultSet), identifiers.toArray());
    }

    public boolean lockById(String transactionId) {
        return !jdbcTemplate.query("SELECT id FROM ledger_transaction WHERE id = ? FOR UPDATE",
                (resultSet, rowNumber) -> resultSet.getString("id"), transactionId).isEmpty();
    }

    /** Used only by trusted initialization/integration code; no financial update method exists. */
    public void insert(LedgerTransaction transaction) {
        jdbcTemplate.update("""
                INSERT INTO ledger_transaction(id, account_id, provider_reference, merchant, amount,
                    currency, booked_at, status, category, observed_at, reversal_of)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, transaction.id(), transaction.accountId(), transaction.reference(), transaction.merchant(),
                transaction.amount(), transaction.currency(), OffsetDateTime.ofInstant(transaction.bookedAt(), ZoneOffset.UTC),
                transaction.status(), transaction.category(), OffsetDateTime.ofInstant(transaction.observedAt(), ZoneOffset.UTC),
                transaction.reversalOf());
    }

    private LedgerTransaction mapTransaction(ResultSet resultSet) throws SQLException {
        return new LedgerTransaction(
                resultSet.getString("id"),
                resultSet.getString("account_id"),
                resultSet.getString("merchant"),
                resultSet.getString("provider_reference"),
                resultSet.getBigDecimal("amount"),
                resultSet.getString("currency"),
                resultSet.getObject("booked_at", OffsetDateTime.class).toInstant(),
                resultSet.getString("status"),
                resultSet.getString("category"),
                resultSet.getObject("observed_at", OffsetDateTime.class).toInstant(),
                resultSet.getString("reversal_of"),
                resultSet.getString("active_dispute_id"),
                resultSet.getString("latest_dispute_id"),
                resultSet.getBoolean("has_reversal"));
    }
}
