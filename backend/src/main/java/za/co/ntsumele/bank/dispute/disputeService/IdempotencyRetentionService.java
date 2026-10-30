package za.co.ntsumele.bank.dispute.disputeService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.ntsumele.bank.audit.auditService.AuditTrailService;
import za.co.ntsumele.bank.dispute.disputeRepository.DisputeIdempotencyRepository;

/**
 * class : IdempotencyRetentionService
 * Author : Nyiko Chauke
 * 2026
 *
 * Deletes a bounded batch of replay records only after both the record and its resolved case have aged
 * beyond the configured retention window. Records the aggregate cleanup action atomically while retaining
 * all dispute cases, histories and existing audit events; locked rows are skipped for concurrent workers.
 */
@Service
public class IdempotencyRetentionService {
    private final DisputeIdempotencyRepository repository;
    private final AuditTrailService auditTrail;
    private final Clock clock;
    private final Duration retention;
    private final int batchSize;

    public IdempotencyRetentionService(
            DisputeIdempotencyRepository repository, AuditTrailService auditTrail, Clock clock,
            @Value("${app.idempotency.retention:90d}") Duration retention,
            @Value("${app.idempotency.cleanup.batch-size:500}") int batchSize) {
        if (retention.isNegative() || retention.isZero() || batchSize < 1 || batchSize > 5000) {
            throw new IllegalArgumentException("Idempotency retention must be positive and batch size must be 1 to 5000.");
        }
        this.repository = repository;
        this.auditTrail = auditTrail;
        this.clock = clock;
        this.retention = retention;
        this.batchSize = batchSize;
    }

    @Transactional(timeout = 20)
    public int removeExpiredRecords() {
        Instant cutoff = clock.instant().minus(retention);
        int removed = repository.deleteExpiredClosedCases(cutoff, batchSize);
        if (removed > 0) {
            auditTrail.recordAction("system", "IDEMPOTENCY_RECORDS_PURGED", null,
                    "Removed " + removed + " expired replay records for resolved cases; cutoff=" + cutoff);
        }
        return removed;
    }
}
