package za.co.ntsumele.bank.audit.auditService;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.ntsumele.bank.audit.auditModel.AuditEvent;
import za.co.ntsumele.bank.audit.auditRepository.AuditEventRepository;

/**
 * class : AuditTrailService
 * Author : Nyiko Chauke
 * 2026
 *
 * Creates audit entries with unique IDs and records significant application actions through the audit repository.
 * Uses the shared clock for new events and accepts original event times from trusted bootstrap data.
 */
@Service
@Transactional
public class AuditTrailService {

    private final AuditEventRepository auditEventRepository;
    private final Clock clock;

    public AuditTrailService(AuditEventRepository auditEventRepository, Clock clock) {
        this.auditEventRepository = auditEventRepository;
        this.clock = clock;
    }

    public void recordAction(String actor, String action, String entityId, String changeSummary) {
        recordActionAt(actor, action, entityId, changeSummary, clock.instant());
    }

    /** Allows trusted bootstrap data to retain the original event time. */
    public void recordActionAt(
            String actor, String action, String entityId, String changeSummary, Instant occurredAt) {
        AuditEvent event = new AuditEvent(
                UUID.randomUUID().toString(), actor, action, entityId, occurredAt, changeSummary);
        auditEventRepository.insert(event);
    }
}
