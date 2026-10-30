package za.co.ntsumele.bank.audit.auditModel;

import java.time.Instant;

/**
 * class : AuditEvent
 * Author : Nyiko Chauke
 * 2026
 *
 * Represents an audit entry containing the actor, action, affected entity, event time and change summary.
 */
public record AuditEvent(
        String id,
        String actor,
        String action,
        String entityId,
        Instant occurredAt,
        String changeSummary) {
}
