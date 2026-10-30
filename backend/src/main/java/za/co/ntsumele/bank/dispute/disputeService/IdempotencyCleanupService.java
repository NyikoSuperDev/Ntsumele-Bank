package za.co.ntsumele.bank.dispute.disputeService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * class : IdempotencyCleanupService
 * Author : Nyiko Chauke
 * 2026
 *
 * Schedules one bounded retention batch per interval and leaves transaction ownership to its worker.
 * A failed run is logged and retried at the next interval; active cases and case history are not cleanup targets.
 */
@Service
@ConditionalOnProperty(prefix = "app.idempotency.cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IdempotencyCleanupService {
    private static final Logger LOGGER = LoggerFactory.getLogger(IdempotencyCleanupService.class);
    private final IdempotencyRetentionService retentionService;

    public IdempotencyCleanupService(IdempotencyRetentionService retentionService) {
        this.retentionService = retentionService;
    }

    @Scheduled(fixedDelayString = "${app.idempotency.cleanup.fixed-delay:PT1H}",
               initialDelayString = "${app.idempotency.cleanup.initial-delay:PT5M}")
    public void cleanExpiredRecords() {
        try {
            retentionService.removeExpiredRecords();
        } catch (RuntimeException exception) {
            LOGGER.warn("Idempotency cleanup did not complete; it will retry at the next interval", exception);
        }
    }
}
