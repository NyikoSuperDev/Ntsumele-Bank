package za.co.ntsumele.bank.dispute.disputeRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.ntsumele.bank.dispute.disputeModel.DisputeCase;
import za.co.ntsumele.bank.dispute.disputeModel.DisputeCaseCounts;
import za.co.ntsumele.bank.dispute.disputeModel.DisputeSearchResult;
import za.co.ntsumele.bank.dispute.disputeModel.DisputeReason;
import za.co.ntsumele.bank.dispute.disputeModel.DisputeStatus;

/**
 * class : DisputeCaseRepository
 * Author : Nyiko Chauke
 * 2026
 *
 * Queries bounded customer/staff case pages and aggregate workspace counts, and maps persisted cases.
 * Writes case creation and version-checked updates, allocates sequence-backed references and supplies row locks.
 */
@Repository
public class DisputeCaseRepository {
    private final JdbcTemplate jdbcTemplate;

    public DisputeCaseRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DisputeSearchResult findAuthorizedPage(String username, String status, int page, int size) {
        return findPage(username, status, page, size);
    }

    public DisputeSearchResult findStaffPage(String status, int page, int size) {
        return findPage(null, status, page, size);
    }

    private DisputeSearchResult findPage(String username, String status, int page, int size) {
        List<Object> parameters = new ArrayList<>();
        StringBuilder query = caseScope(username, parameters);
        appendStatusFilter(query, parameters, status);
        long total = jdbcTemplate.queryForObject("SELECT COUNT(*)" + query, Long.class, parameters.toArray());
        parameters.add(size);
        parameters.add((long) page * size);
        List<DisputeCase> content = jdbcTemplate.query(
                "SELECT dispute.*" + query + " ORDER BY dispute.created_at DESC, dispute.id DESC LIMIT ? OFFSET ?",
                (resultSet, rowNumber) -> mapDispute(resultSet), parameters.toArray());
        int totalPages = (int) Math.min(Integer.MAX_VALUE, total / size + (total % size == 0 ? 0 : 1));
        return new DisputeSearchResult(content, total, totalPages, page, size);
    }

    public DisputeCaseCounts countAuthorizedCases(String username) {
        return countCases(username);
    }

    public DisputeCaseCounts countStaffCases() {
        return countCases(null);
    }

    private DisputeCaseCounts countCases(String username) {
        List<Object> parameters = new ArrayList<>();
        StringBuilder query = caseScope(username, parameters);
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) AS total,
                       COUNT(*) FILTER (WHERE dispute.status <> 'RESOLVED') AS active,
                       COUNT(*) FILTER (WHERE dispute.status = 'AWAITING_INFORMATION') AS awaiting_information,
                       COUNT(*) FILTER (WHERE dispute.status = 'RESOLVED') AS resolved
                """ + query, (resultSet, rowNumber) -> new DisputeCaseCounts(
                        resultSet.getLong("total"), resultSet.getLong("active"),
                        resultSet.getLong("awaiting_information"), resultSet.getLong("resolved")), parameters.toArray());
    }

    private StringBuilder caseScope(String username, List<Object> parameters) {
        StringBuilder query = new StringBuilder("""
                 FROM dispute_case dispute
                 JOIN ledger_transaction transaction_record ON transaction_record.id = dispute.transaction_id
                """);
        if (username == null) {
            query.append(" WHERE 1 = 1");
        } else {
            query.append("""
                     JOIN account_access entitlement ON entitlement.account_id = transaction_record.account_id
                     WHERE entitlement.username = ?
                    """);
            parameters.add(username);
        }
        return query;
    }

    public long nextReferenceNumber() {
        return jdbcTemplate.queryForObject("SELECT nextval('dispute_case_reference_seq')", Long.class);
    }

    public Optional<DisputeCase> findById(String disputeId) {
        return jdbcTemplate.query("SELECT * FROM dispute_case WHERE id = ?",
                (resultSet, rowNumber) -> mapDispute(resultSet), disputeId).stream().findFirst();
    }

    public boolean lockById(String disputeId) {
        return !jdbcTemplate.query("SELECT id FROM dispute_case WHERE id = ? FOR UPDATE",
                (resultSet, rowNumber) -> resultSet.getString("id"), disputeId).isEmpty();
    }

    public void insert(DisputeCase disputeCase) {
        String activeTransactionId = disputeCase.status() == DisputeStatus.RESOLVED
                ? null : disputeCase.transactionId();
        jdbcTemplate.update("""
                INSERT INTO dispute_case(id, case_reference, transaction_id, submitted_by, reason,
                    description, status, active_transaction_id, created_at, updated_at, resolution, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, disputeCase.id(), disputeCase.caseReference(), disputeCase.transactionId(), disputeCase.submittedBy(),
                disputeCase.reason().name(), disputeCase.description(), disputeCase.status().name(), activeTransactionId,
                OffsetDateTime.ofInstant(disputeCase.createdAt(), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(disputeCase.updatedAt(), ZoneOffset.UTC), disputeCase.resolution(), disputeCase.version());
    }

    public int updateStatus(String disputeId, DisputeStatus status, String activeTransactionId,
                            Instant updatedAt, String resolution, long expectedVersion) {
        return jdbcTemplate.update("""
                UPDATE dispute_case
                SET status = ?, active_transaction_id = ?, updated_at = ?, resolution = ?, version = version + 1
                WHERE id = ? AND version = ?
                """, status.name(), activeTransactionId, OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC),
                resolution, disputeId, expectedVersion);
    }

    private void appendStatusFilter(StringBuilder query, List<Object> parameters, String status) {
        if ("ACTIVE".equals(status)) {
            query.append(" AND dispute.status <> 'RESOLVED'");
        } else if (!status.isBlank()) {
            query.append(" AND dispute.status = ?");
            parameters.add(status);
        }
    }

    private DisputeCase mapDispute(ResultSet resultSet) throws SQLException {
        return new DisputeCase(
                resultSet.getString("id"),
                resultSet.getString("case_reference"),
                resultSet.getString("transaction_id"),
                resultSet.getString("submitted_by"),
                DisputeReason.valueOf(resultSet.getString("reason")),
                resultSet.getString("description"),
                DisputeStatus.valueOf(resultSet.getString("status")),
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
                resultSet.getObject("updated_at", OffsetDateTime.class).toInstant(),
                resultSet.getString("resolution"),
                resultSet.getLong("version"));
    }
}
