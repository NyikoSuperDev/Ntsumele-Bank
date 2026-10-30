package za.co.ntsumele.bank.dispute.disputeRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Collection;
import java.util.Map;
import java.util.HashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.ntsumele.bank.dispute.disputeModel.DisputeHistoryEntry;
import za.co.ntsumele.bank.dispute.disputeModel.DisputeStatus;

/**
 * class : DisputeHistoryRepository
 * Author : Nyiko Chauke
 * 2026
 *
 * Stores case activity entries and retrieves each timeline in timestamp and event-sequence order.
 * Sequence ordering keeps entries stable when event timestamps are equal; grouped activity counts
 * support paged case summaries without loading complete histories.
 */
@Repository
public class DisputeHistoryRepository {
    private final JdbcTemplate jdbcTemplate;

    public DisputeHistoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DisputeHistoryEntry> findByDisputeId(String disputeId) {
        return jdbcTemplate.query("""
                SELECT * FROM dispute_history
                WHERE dispute_id = ?
                ORDER BY occurred_at, event_sequence
                """, (resultSet, rowNumber) -> {
                    String fromStatus = resultSet.getString("from_status");
                    return new DisputeHistoryEntry(
                            resultSet.getString("id"),
                            resultSet.getString("dispute_id"),
                            fromStatus == null ? null : DisputeStatus.valueOf(fromStatus),
                            DisputeStatus.valueOf(resultSet.getString("to_status")),
                            resultSet.getString("actor"),
                            resultSet.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                            resultSet.getString("note"));
                }, disputeId);
    }

    public Map<String, Long> countByDisputeIds(Collection<String> disputeIds) {
        if (disputeIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(disputeIds.size(), "?"));
        Map<String, Long> counts = new HashMap<>();
        jdbcTemplate.query("SELECT dispute_id, COUNT(*) AS entries FROM dispute_history WHERE dispute_id IN ("
                        + placeholders + ") GROUP BY dispute_id", resultSet -> {
                            counts.put(resultSet.getString("dispute_id"), resultSet.getLong("entries"));
                        }, disputeIds.toArray());
        return Map.copyOf(counts);
    }

    public void insert(DisputeHistoryEntry entry) {
        jdbcTemplate.update("""
                INSERT INTO dispute_history(id, dispute_id, from_status, to_status, actor, occurred_at, note)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, entry.id(), entry.disputeId(), entry.fromStatus() == null ? null : entry.fromStatus().name(),
                entry.toStatus().name(), entry.actor(), OffsetDateTime.ofInstant(entry.occurredAt(), ZoneOffset.UTC), entry.note());
    }
}
