package za.co.ntsumele.bank.identity.identityRepository;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.ntsumele.bank.identity.identityModel.BankIdentity;

/**
 * class : BankIdentityRepository
 * Author : Nyiko Chauke
 * 2026
 *
 * Loads and stores bank identities and acquires a principal-row lock for surrounding
 * dispute transactions. It also supplies the identity count used by demo initialization.
 */
@Repository
public class BankIdentityRepository {
    private final JdbcTemplate jdbcTemplate;

    public BankIdentityRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<BankIdentity> findByUsername(String username) {
        return jdbcTemplate.query("""
                SELECT username, display_name, password_hash, role
                FROM bank_user WHERE username = ?
                """, (resultSet, rowNumber) -> new BankIdentity(
                        resultSet.getString("username"),
                        resultSet.getString("display_name"),
                        resultSet.getString("password_hash"),
                        resultSet.getString("role")), username)
                .stream().findFirst();
    }

    /** Holds the principal row lock until the surrounding service transaction completes. */
    public boolean lockByUsername(String username) {
        return !jdbcTemplate.query("SELECT username FROM bank_user WHERE username = ? FOR UPDATE",
                (resultSet, rowNumber) -> resultSet.getString("username"), username).isEmpty();
    }

    public long count() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM bank_user", Long.class);
    }

    public void insert(BankIdentity user) {
        jdbcTemplate.update("""
                INSERT INTO bank_user(username, display_name, password_hash, role)
                VALUES (?, ?, ?, ?)
                """, user.username(), user.displayName(), user.passwordHash(), user.role());
    }
}
