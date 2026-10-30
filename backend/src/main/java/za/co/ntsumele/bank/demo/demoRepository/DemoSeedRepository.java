package za.co.ntsumele.bank.demo.demoRepository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * class : DemoSeedRepository
 * Author : Nyiko Chauke
 * 2026
 *
 * Acquires a PostgreSQL advisory lock within an existing transaction to serialize demo initialization across application instances.
 * The lock is released when that transaction commits or rolls back.
 */
@Repository
public class DemoSeedRepository {

    private final JdbcTemplate jdbcTemplate;

    public DemoSeedRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockInitialization() {
        // A transaction-scoped, application-owned key; released on commit or rollback.
        jdbcTemplate.execute("SELECT pg_advisory_xact_lock(1937007981, 1)");
    }
}
