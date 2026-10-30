package za.co.ntsumele.bank.audit.auditRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.ntsumele.bank.audit.auditModel.AuditEvent;

/**
 * class : AuditEventRepository
 * Author : Nyiko Chauke
 * 2026
 *
 * Inserts audit entries into PostgreSQL using bound parameters and UTC event timestamps.
 */
@Repository
public class AuditEventRepository {
    private final JdbcTemplate jdbcTemplate;

    public AuditEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(AuditEvent event) {
        jdbcTemplate.update("""
                INSERT INTO audit_event(id, actor, action, entity_id, occurred_at, change_summary)
                VALUES (?, ?, ?, ?, ?, ?)
                """, event.id(), event.actor(), event.action(), event.entityId(),
                OffsetDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC), event.changeSummary());
    }
}
