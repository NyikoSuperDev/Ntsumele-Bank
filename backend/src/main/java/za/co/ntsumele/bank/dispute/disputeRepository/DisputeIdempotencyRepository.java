package za.co.ntsumele.bank.dispute.disputeRepository;

import java.time.OffsetDateTime;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.ntsumele.bank.dispute.disputeModel.DisputeIdempotencyRecord;

/**
 * class : DisputeIdempotencyRepository
 * Author : Nyiko Chauke
 * 2026
 *
 * Stores and retrieves request-key records scoped to a customer username.
 * Persists the payload hashes and case links used by submission replay and conflict checks,
 * and removes bounded batches of expired records linked only to long-resolved cases.
 */
@Repository
public class DisputeIdempotencyRepository {
    private final JdbcTemplate jdbcTemplate;

    public DisputeIdempotencyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<DisputeIdempotencyRecord> findByUsernameAndRequestKey(String username, String requestKey) {
        return jdbcTemplate.query("""
                SELECT username, request_key, payload_hash, dispute_id, created_at
                FROM idempotency_record WHERE username = ? AND request_key = ?
                """, (resultSet, rowNumber) -> new DisputeIdempotencyRecord(
                        resultSet.getString("username"),
                        resultSet.getString("request_key"),
                        resultSet.getString("payload_hash"),
                        resultSet.getString("dispute_id"),
                        resultSet.getObject("created_at", OffsetDateTime.class).toInstant()), username, requestKey)
                .stream().findFirst();
    }

    public int deleteExpiredClosedCases(Instant cutoff, int batchSize) {
        OffsetDateTime expiry = OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC);
        return jdbcTemplate.update("""
                WITH expired AS (
                    SELECT replay.username, replay.request_key
                    FROM idempotency_record replay
                    JOIN dispute_case dispute ON dispute.id = replay.dispute_id
                    WHERE replay.created_at < ? AND dispute.status = 'RESOLVED' AND dispute.updated_at < ?
                    ORDER BY replay.created_at, replay.username, replay.request_key
                    LIMIT ? FOR UPDATE OF replay SKIP LOCKED
                )
                DELETE FROM idempotency_record replay USING expired
                WHERE replay.username = expired.username AND replay.request_key = expired.request_key
                """, expiry, expiry, batchSize);
    }

    public void insert(DisputeIdempotencyRecord record) {
        jdbcTemplate.update("""
                INSERT INTO idempotency_record(username, request_key, payload_hash, dispute_id, created_at)
                VALUES (?, ?, ?, ?, ?)
                """, record.username(), record.requestKey(), record.payloadHash(), record.disputeId(),
                OffsetDateTime.ofInstant(record.createdAt(), ZoneOffset.UTC));
    }
}
