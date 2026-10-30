package za.co.ntsumele.bank.account.accountRepository;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.ntsumele.bank.account.accountModel.BankAccount;

/**
 * class : AccountRepository
 * Author : Nyiko Chauke
 * 2026
 *
 * Reads accounts available to a customer and checks account, transaction and dispute
 * entitlements. It also persists account records and access grants for trusted initialization.
 */
@Repository
public class AccountRepository {
    private final JdbcTemplate jdbcTemplate;

    public AccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<BankAccount> findAuthorizedAccounts(String username) {
        return jdbcTemplate.query("""
                SELECT account.*
                FROM bank_account account
                JOIN account_access entitlement ON entitlement.account_id = account.id
                WHERE entitlement.username = ?
                ORDER BY account.id
                """, (resultSet, rowNumber) -> new BankAccount(
                        resultSet.getString("id"),
                        resultSet.getString("name"),
                        resultSet.getString("masked_number"),
                        resultSet.getString("currency"),
                        resultSet.getBigDecimal("balance")), username);
    }

    public boolean hasAccountAccess(String username, String accountId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM account_access
                WHERE username = ? AND account_id = ?
                """, Long.class, username, accountId) > 0;
    }

    public boolean hasTransactionAccess(String username, String transactionId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM ledger_transaction transaction_record
                JOIN account_access entitlement ON entitlement.account_id = transaction_record.account_id
                WHERE entitlement.username = ? AND transaction_record.id = ?
                """, Long.class, username, transactionId) > 0;
    }

    public boolean hasDisputeAccess(String username, String disputeId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM dispute_case dispute
                JOIN ledger_transaction transaction_record ON transaction_record.id = dispute.transaction_id
                JOIN account_access entitlement ON entitlement.account_id = transaction_record.account_id
                WHERE entitlement.username = ? AND dispute.id = ?
                """, Long.class, username, disputeId) > 0;
    }

    public void insert(BankAccount account) {
        jdbcTemplate.update("""
                INSERT INTO bank_account(id, name, masked_number, currency, balance)
                VALUES (?, ?, ?, ?, ?)
                """, account.id(), account.name(), account.maskedNumber(), account.currency(), account.balance());
    }

    public void grantAccess(String username, String accountId) {
        jdbcTemplate.update("INSERT INTO account_access(username, account_id) VALUES (?, ?)", username, accountId);
    }
}
