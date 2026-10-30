package za.co.ntsumele.bank.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletContext;
import java.net.URI;
import java.sql.DriverManager;
import java.sql.Connection;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.jdbc.datasource.AbstractDataSource;
import za.co.ntsumele.bank.account.accountRepository.AccountRepository;
import za.co.ntsumele.bank.dispute.disputeRepository.DisputeCaseRepository;
import za.co.ntsumele.bank.dispute.disputeRepository.DisputeHistoryRepository;
import za.co.ntsumele.bank.dispute.disputeService.IdempotencyRetentionService;
import za.co.ntsumele.bank.transaction.transactionRepository.LedgerTransactionRepository;
import za.co.ntsumele.bank.transaction.transactionService.TransactionInquiryService;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import za.co.ntsumele.bank.dispute.disputeDto.DisputeHistoryEventResponse;
import za.co.ntsumele.bank.dispute.disputeDto.DisputeInformationRequest;
import za.co.ntsumele.bank.dispute.disputeDto.DisputeStatusUpdateRequest;
import za.co.ntsumele.bank.dispute.disputeDto.DisputeSubmissionRequest;
import za.co.ntsumele.bank.dispute.disputeModel.DisputeReason;
import za.co.ntsumele.bank.dispute.disputeModel.DisputeStatus;
import za.co.ntsumele.bank.dispute.disputeService.DisputeInformationService;
import za.co.ntsumele.bank.dispute.disputeService.DisputeInquiryService;
import za.co.ntsumele.bank.dispute.disputeService.DisputeReviewService;
import za.co.ntsumele.bank.dispute.disputeService.DisputeSubmissionService;
import za.co.ntsumele.bank.failure.failureException.BankingRuleViolation;
import za.co.ntsumele.bank.http.httpFilter.JsonRequestSizeFilter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * class : BankingPostgresIT
 * Author : Nyiko Chauke
 * 2026
 *
 * Exercises banking endpoints and services against a dedicated PostgreSQL test database with the real Spring Security filter chain.
 * Verifies account isolation, immutable ledger values, idempotency, concurrent duplicates, case lifecycles, histories, dispute filters and database timeout handling.
 * Requires a disposable database whose name ends in _test because the suite clears and replaces test records.
 */
@SpringBootTest(properties = {"app.idempotency.cleanup.enabled=false", "app.idempotency.cleanup.batch-size=2"})
@AutoConfigureMockMvc
@ActiveProfiles("demo")
// No mocks are used, so tests do not need a runtime Java-agent attachment.
@TestExecutionListeners(
        listeners = DependencyInjectionTestExecutionListener.class,
        mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
class BankingPostgresIT {

    @DynamicPropertySource
    static void useDedicatedPostgresDatabase(DynamicPropertyRegistry properties) {
        String url = requiredTestSetting("TEST_DATABASE_URL");
        if (!url.startsWith("jdbc:postgresql://")
                || !URI.create(url.substring("jdbc:".length())).getPath().matches("/[a-zA-Z0-9_]+_test")) {
            throw new IllegalStateException(
                    "TEST_DATABASE_URL must target a PostgreSQL database whose name ends in _test; "
                    + "this integration suite deletes and replaces test data.");
        }
        String username = requiredTestSetting("TEST_DATABASE_USERNAME");
        String password = requiredTestSetting("TEST_DATABASE_PASSWORD");
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> username);
        properties.add("spring.datasource.password", () -> password);
        properties.add("spring.datasource.hikari.data-source-properties.sslmode", () -> "disable");
    }

    private static String requiredTestSetting(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for the postgres-integration profile; "
                    + "use a dedicated disposable PostgreSQL test database.");
        }
        return value;
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DisputeInquiryService disputeInquiryService;

    @Autowired
    private DisputeSubmissionService disputeSubmissionService;

    @Autowired
    private DisputeReviewService disputeReviewService;

    @Autowired
    private DisputeInformationService disputeInformationService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IdempotencyRetentionService idempotencyRetentionService;

    @Autowired
    private TransactionInquiryService transactionInquiryService;

    @BeforeEach
    void resetDisputesAndRefreshSyntheticLedger() {
        jdbcTemplate.update("DELETE FROM idempotency_record");
        jdbcTemplate.update("DELETE FROM dispute_history");
        jdbcTemplate.update("DELETE FROM dispute_case");
        jdbcTemplate.update("DELETE FROM audit_event");
        jdbcTemplate.update("UPDATE ledger_transaction SET observed_at=?", OffsetDateTime.now());
    }

    @Test
    void postgresStatementTimeoutIsTranslatedAndThePooledConnectionRemainsUsable() {
        assertThatThrownBy(() -> jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("SET statement_timeout = '100ms'");
                try {
                    statement.execute("SELECT pg_sleep(0.3)");
                } finally {
                    statement.execute("RESET statement_timeout");
                }
            }
            return null;
        })).isInstanceOf(QueryTimeoutException.class).satisfies(exception -> {
            Throwable cause = ((QueryTimeoutException) exception).getMostSpecificCause();
            assertThat(cause).isInstanceOf(SQLException.class);
            assertThat(((SQLException) cause).getSQLState()).isEqualTo("57014");
        });
        assertThat(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
    }

    @Test
    void committedCasesAndHistoryAreVisibleFromAnIndependentPostgresConnection() throws Exception {
        var dispute = disputeSubmissionService.submitDispute("amy", "independent-connection", createRequest("tx-1001"));
        try (var connection = DriverManager.getConnection(requiredTestSetting("TEST_DATABASE_URL"),
                requiredTestSetting("TEST_DATABASE_USERNAME"), requiredTestSetting("TEST_DATABASE_PASSWORD"))) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
            try (var query = connection.prepareStatement("""
                    SELECT d.transaction_id, t.amount, h.to_status, h.actor
                    FROM dispute_case d
                    JOIN ledger_transaction t ON t.id=d.transaction_id
                    JOIN dispute_history h ON h.dispute_id=d.id
                    WHERE d.id=?
                    """)) {
                query.setString(1, dispute.id());
                try (var rows = query.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("transaction_id")).isEqualTo("tx-1001");
                    assertThat(rows.getBigDecimal("amount")).isEqualByComparingTo(dispute.transaction().amount());
                    assertThat(rows.getString("to_status")).isEqualTo("SUBMITTED");
                    assertThat(rows.getString("actor")).isEqualTo("amy");
                    assertThat(rows.next()).isFalse();
                }
            }
        }
    }

    @Test
    void postgresLockTimeoutRollsBackAndTheSameIdempotencyKeyCanBeRetried() throws Exception {
        String requestKey = "postgres-lock-timeout";
        try (var lockOwner = DriverManager.getConnection(requiredTestSetting("TEST_DATABASE_URL"),
                requiredTestSetting("TEST_DATABASE_USERNAME"), requiredTestSetting("TEST_DATABASE_PASSWORD"))) {
            lockOwner.setAutoCommit(false);
            try (var query = lockOwner.prepareStatement(
                    "SELECT id FROM ledger_transaction WHERE id='tx-1001' FOR UPDATE")) {
                try (var rows = query.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                }
            }

            // A separate PostgreSQL session owns the lock until this transaction is rolled back.
            mockMvc.perform(post("/api/disputes").with(user("amy").roles("CUSTOMER")).with(csrf())
                    .header("Idempotency-Key", requestKey)
                    .contentType("application/json").content(requestBody("tx-1001")))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().string("Retry-After", "1"))
                    .andExpect(jsonPath("$.code").value("BUSY"));
            for (String table : List.of("dispute_case", "dispute_history", "idempotency_record", "audit_event")) {
                assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class))
                        .as("No partially committed rows in %s after PostgreSQL cancels the locked statement", table)
                        .isZero();
            }
            lockOwner.rollback();
        }

        var retryResult = mockMvc.perform(post("/api/disputes").with(user("amy").roles("CUSTOMER")).with(csrf())
                .header("Idempotency-Key", requestKey)
                .contentType("application/json").content(requestBody("tx-1001")))
                .andExpect(status().isCreated()).andReturn();
        String createdId = objectMapper.readTree(retryResult.getResponse().getContentAsString()).get("id").asText();
        assertThat(disputeSubmissionService.submitDispute("amy", requestKey, createRequest("tx-1001")).id())
                .isEqualTo(createdId);
        for (String table : List.of("dispute_case", "dispute_history", "idempotency_record", "audit_event")) {
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class))
                    .as("The retry commits exactly one row in %s", table).isEqualTo(1L);
        }
    }

    @Test
    void authenticationCsrfAndRolesAreEnforced() throws Exception {
        mockMvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/login")
                .param("username", "amy").param("password", "Ntsumele!2026"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/login").with(csrf())
                .param("username", "amy").param("password", "Ntsumele!2026"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/login").with(csrf())
                .param("username", "amy").param("password", "wrong"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/staff/disputes").with(user("amy").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/accounts").with(user("reviewer").roles("STAFF")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/disputes").with(user("amy").roles("CUSTOMER"))
                .contentType("application/json").content(requestBody("tx-1001")))
                .andExpect(status().isForbidden());
    }

    @Test
    void realSessionLoginCsrfMutationAndLogoutFollowSpaContract() throws Exception {
        // Exercise issued tokens and the real security chain without authentication shortcuts.
        var anonymousCsrfResult = mockMvc.perform(get("/api/csrf"))
                .andExpect(status().isOk()).andReturn();
        var csrfBeforeLogin = objectMapper.readTree(anonymousCsrfResult.getResponse().getContentAsString());
        var anonymousSession = (MockHttpSession) anonymousCsrfResult.getRequest().getSession(false);
        String anonymousSessionId = anonymousSession.getId();
        var loginResult = mockMvc.perform(post("/api/login").session(anonymousSession)
                .header(csrfBeforeLogin.get("headerName").asText(), csrfBeforeLogin.get("token").asText())
                .formField("username", "amy").formField("password", "Ntsumele!2026"))
                .andExpect(status().isOk()).andReturn();
        var customerSession = (MockHttpSession) loginResult.getRequest().getSession(false);

        assertThat(customerSession).isNotNull();
        assertThat(customerSession.getId()).isNotEqualTo(anonymousSessionId);
        mockMvc.perform(get("/api/me").session(customerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("amy"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
        mockMvc.perform(get("/api/accounts").session(customerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("acc-everyday"))
                .andExpect(jsonPath("$[1].id").value("acc-savings"));
        mockMvc.perform(get("/api/transactions?accountId=acc-ben").session(customerSession))
                .andExpect(status().isNotFound());

        var customerCsrfResult = mockMvc.perform(get("/api/csrf").session(customerSession))
                .andExpect(status().isOk()).andReturn();
        var customerCsrf = objectMapper.readTree(customerCsrfResult.getResponse().getContentAsString());
        mockMvc.perform(post("/api/disputes").session(customerSession)
                .header(customerCsrf.get("headerName").asText(), customerCsrf.get("token").asText())
                .header("Idempotency-Key", "real-session-dispute")
                .contentType("application/json").content(requestBody("tx-1001")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transaction.id").value("tx-1001"))
                .andExpect(jsonPath("$.history[0].actor").value("amy"));
        mockMvc.perform(post("/api/logout").session(customerSession)
                .header(customerCsrf.get("headerName").asText(), customerCsrf.get("token").asText()))
                .andExpect(status().isNoContent());
        assertThat(customerSession.isInvalid()).isTrue();
        mockMvc.perform(get("/api/me").session(customerSession)).andExpect(status().isUnauthorized());
    }

    @Test
    void jsonBodiesAreBoundedWithKnownOrUnknownContentLength() throws Exception {
        String oversizedRequest = "{\"transactionId\":\"tx-1001\",\"reason\":\"UNRECOGNIZED\","
                + "\"description\":\"" + "x".repeat(JsonRequestSizeFilter.MAX_BYTES) + "\"}";
        mockMvc.perform(post("/api/disputes").with(user("amy").roles("CUSTOMER")).with(csrf())
                .header("Idempotency-Key", "body-limit-known")
                .contentType("application/json").content(oversizedRequest))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
        mockMvc.perform(new UnknownLengthJsonRequestBuilder("/api/disputes")
                .with(user("amy").roles("CUSTOMER")).with(csrf())
                .header("Idempotency-Key", "body-limit-unknown").header("Transfer-Encoding", "chunked")
                .contentType("application/json").content(oversizedRequest))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dispute_case", Long.class)).isZero();
        mockMvc.perform(post("/api/disputes").with(user("amy").roles("CUSTOMER")).with(csrf())
                .header("Idempotency-Key", "body-limit-accepted")
                .contentType("application/json").content(requestBody("tx-1001")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transaction.id").value("tx-1001"));
    }

    @Test
    void accountAndTransactionAccessAreScoped() throws Exception {
        mockMvc.perform(get("/api/accounts").with(user("amy").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/transactions?accountId=acc-ben").with(user("amy").roles("CUSTOMER")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/transactions").with(user("amy").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(14));
        mockMvc.perform(post("/api/disputes").with(user("amy").roles("CUSTOMER")).with(csrf())
                .header("Idempotency-Key", "test-foreign-1")
                .contentType("application/json").content(requestBody("tx-2001")))
                .andExpect(status().isNotFound());

        var otherCustomersDispute = disputeSubmissionService.submitDispute("ben", "ben-case-1", createRequest("tx-2001"));
        mockMvc.perform(get("/api/disputes/" + otherCustomersDispute.id())
                .with(user("amy").roles("CUSTOMER"))).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/disputes/" + otherCustomersDispute.id() + "/information")
                .with(user("amy").roles("CUSTOMER")).with(csrf()).contentType("application/json")
                .content("{\"message\":\"More information\",\"version\":0}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void tamperingIsRejectedAndLedgerRemainsUnchanged() throws Exception {
        var originalTransaction = jdbcTemplate.queryForMap("SELECT * FROM ledger_transaction WHERE id='tx-1001'");
        mockMvc.perform(post("/api/disputes").with(user("amy").roles("CUSTOMER")).with(csrf())
                .header("Idempotency-Key", "tamper-123").contentType("application/json")
                .content("""
                        {"transactionId":"tx-1001","reason":"UNRECOGNIZED",
                         "description":"I do not recognize this purchase.",
                         "amount":0.01,"owner":"ben","reference":"fake"}
                        """))
                .andExpect(status().isBadRequest());
        var dispute = disputeSubmissionService.submitDispute("amy", "normal-123", createRequest("tx-1001"));
        disputeReviewService.updateStatus("reviewer", dispute.id(),
                new DisputeStatusUpdateRequest(DisputeStatus.UNDER_REVIEW, "Investigation started", 0L));
        disputeReviewService.updateStatus("reviewer", dispute.id(),
                new DisputeStatusUpdateRequest(DisputeStatus.RESOLVED, "Investigation completed", 1L));
        assertThat(jdbcTemplate.queryForMap("SELECT * FROM ledger_transaction WHERE id='tx-1001'"))
                .isEqualTo(originalTransaction);
    }

    @Test
    void onlyFreshBookedDebitsWithoutReversalsAreEligible() {
        for (String transactionId : List.of("tx-1006", "tx-1007", "tx-1011", "tx-1012")) {
            assertThatThrownBy(() -> disputeSubmissionService.submitDispute(
                    "amy", "ineligible-" + transactionId, createRequest(transactionId)))
                    .isInstanceOf(BankingRuleViolation.class).hasMessageContaining("booked debit");
        }
        jdbcTemplate.update("UPDATE ledger_transaction SET observed_at=? WHERE id='tx-1001'",
                OffsetDateTime.now().minusDays(2));
        assertThatThrownBy(() -> disputeSubmissionService.submitDispute("amy", "stale-tx-1", createRequest("tx-1001")))
                .isInstanceOf(BankingRuleViolation.class).hasMessageContaining("fresh");
    }

    @Test
    void idempotencyReplaysAndConflictingReuseFails() {
        var dispute = disputeSubmissionService.submitDispute("amy", "request-key-001", createRequest("tx-1001"));
        assertThat(disputeSubmissionService.submitDispute("amy", "request-key-001", createRequest("tx-1001")).id())
                .isEqualTo(dispute.id());
        assertThatThrownBy(() -> disputeSubmissionService.submitDispute("amy", "request-key-001", createRequest("tx-1002")))
                .isInstanceOf(BankingRuleViolation.class).hasMessageContaining("different request");
        assertThatThrownBy(() -> disputeSubmissionService.submitDispute("amy", "request-key-002", createRequest("tx-1001")))
                .isInstanceOf(BankingRuleViolation.class).hasMessageContaining("active dispute");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dispute_case", Long.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE action='DISPUTE_SUBMITTED'", Long.class)).isEqualTo(1);
    }

    @Test
    void lifecycleVersioningHistoryAndClosedCasesAreRetained() throws Exception {
        var dispute = disputeSubmissionService.submitDispute("amy", "lifecycle-key", createRequest("tx-1001"));
        assertThatThrownBy(() -> disputeReviewService.updateStatus("reviewer", dispute.id(),
                new DisputeStatusUpdateRequest(DisputeStatus.RESOLVED, "Cannot skip review", 0L)))
                .isInstanceOf(BankingRuleViolation.class).hasMessageContaining("not allowed");
        disputeReviewService.updateStatus("reviewer", dispute.id(),
                new DisputeStatusUpdateRequest(DisputeStatus.UNDER_REVIEW, "Review has started", 0L));
        assertThatThrownBy(() -> disputeReviewService.updateStatus("reviewer", dispute.id(),
                new DisputeStatusUpdateRequest(DisputeStatus.AWAITING_INFORMATION, "Please send details", 0L)))
                .isInstanceOf(BankingRuleViolation.class).hasMessageContaining("changed");
        disputeReviewService.updateStatus("reviewer", dispute.id(),
                new DisputeStatusUpdateRequest(DisputeStatus.AWAITING_INFORMATION, "Please confirm delivery", 1L));
        disputeInformationService.provideInformation("amy", dispute.id(),
                new DisputeInformationRequest("The delivery date was yesterday", 2L));
        var closedDispute = disputeReviewService.updateStatus("reviewer", dispute.id(),
                new DisputeStatusUpdateRequest(DisputeStatus.RESOLVED, "Merchant accepted the case", 3L));

        assertThat(closedDispute.history()).hasSize(5);
        assertThat(closedDispute.history().get(3).actor()).isEqualTo("amy");
        assertThat(closedDispute.transaction().activeDisputeId()).isNull();
        assertThat(closedDispute.resolution()).isEqualTo("Merchant accepted the case");
        assertThat(disputeSubmissionService.submitDispute("amy", "lifecycle-key", createRequest("tx-1001")).id())
                .isEqualTo(closedDispute.id());
        assertThat(disputeSubmissionService.submitDispute("amy", "lifecycle-key", createRequest("tx-1001")).history())
                .hasSize(5);
        assertThatThrownBy(() -> disputeReviewService.updateStatus("reviewer", dispute.id(),
                new DisputeStatusUpdateRequest(DisputeStatus.UNDER_REVIEW, "Reopen old case", 4L)))
                .isInstanceOf(BankingRuleViolation.class);
        var newDispute = disputeSubmissionService.submitDispute("amy", "new-case-after-close", createRequest("tx-1001"));
        assertThat(newDispute.id()).isNotEqualTo(dispute.id());
        assertThat(disputeInquiryService.getCustomerDisputes("amy", "", 0, 10).content()).hasSize(2);
        assertThat(disputeInquiryService.getCustomerDispute("amy", dispute.id()).history()).hasSize(5);
        mockMvc.perform(get("/api/disputes?status=RESOLVED").with(user("amy").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void concurrentDuplicateSubmissionsCreateOneCase() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            var startGate = new CountDownLatch(1);
            var submissions = new ArrayList<Future<String>>();
            for (int submissionIndex = 0; submissionIndex < 2; submissionIndex++) {
                final String requestKey = "concurrent-" + submissionIndex;
                submissions.add(executor.submit(() -> {
                    startGate.await();
                    try {
                        return disputeSubmissionService.submitDispute("amy", requestKey, createRequest("tx-1001")).id();
                    } catch (BankingRuleViolation exception) {
                        return exception.getCode();
                    }
                }));
            }
            startGate.countDown();
            var results = List.of(submissions.get(0).get(10, TimeUnit.SECONDS),
                    submissions.get(1).get(10, TimeUnit.SECONDS));
            assertThat(results).contains("ACTIVE_DISPUTE_EXISTS");
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dispute_case", Long.class)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void simultaneousSameKeyRequestsReplayOneCase() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            var startGate = new CountDownLatch(1);
            Callable<String> submitDispute = () -> {
                startGate.await();
                return disputeSubmissionService.submitDispute("amy", "same-concurrent-key", createRequest("tx-1001")).id();
            };
            var firstSubmission = executor.submit(submitDispute);
            var secondSubmission = executor.submit(submitDispute);
            startGate.countDown();
            assertThat(firstSubmission.get(10, TimeUnit.SECONDS))
                    .isEqualTo(secondSubmission.get(10, TimeUnit.SECONDS));
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dispute_case", Long.class)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dispute_history", Long.class)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void historyOrderingIsStableWhenEventTimestampsMatch() {
        var dispute = disputeSubmissionService.submitDispute("amy", "same-time-events", createRequest("tx-1001"));
        disputeReviewService.updateStatus("reviewer", dispute.id(),
                new DisputeStatusUpdateRequest(DisputeStatus.UNDER_REVIEW, "Review has started", 0L));
        disputeReviewService.updateStatus("reviewer", dispute.id(),
                new DisputeStatusUpdateRequest(DisputeStatus.AWAITING_INFORMATION, "Confirm the merchant", 1L));
        jdbcTemplate.update("UPDATE dispute_history SET occurred_at=? WHERE dispute_id=?",
                OffsetDateTime.parse("2026-01-01T00:00:00Z"), dispute.id());
        assertThat(disputeInquiryService.getCustomerDispute("amy", dispute.id()).history())
                .extracting(DisputeHistoryEventResponse::toStatus)
                .containsExactly("SUBMITTED", "UNDER_REVIEW", "AWAITING_INFORMATION");
    }

    @Test
    void databaseConstraintIsFinalDuplicateGuard() {
        disputeSubmissionService.submitDispute("amy", "constraint-test", createRequest("tx-1001"));
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO dispute_case
                SELECT 'illegal','illegal-ref',transaction_id,submitted_by,reason,description,status,
                       active_transaction_id,created_at,updated_at,resolution,version
                FROM dispute_case
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void invalidInputsAndStaffLifecycleEndpointAreValidated() throws Exception {
        mockMvc.perform(get("/api/transactions?size=1000").with(user("amy").roles("CUSTOMER")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/disputes").with(user("amy").roles("CUSTOMER")).with(csrf())
                .contentType("application/json").content(requestBody("tx-1001")))
                .andExpect(status().isBadRequest());
        var dispute = disputeSubmissionService.submitDispute("amy", "staff-endpoint-1", createRequest("tx-1001"));
        mockMvc.perform(post("/api/staff/disputes/" + dispute.id() + "/status")
                .with(user("reviewer").roles("STAFF")).with(csrf()).contentType("application/json")
                .content("{\"status\":\"UNDER_REVIEW\",\"note\":\"Assigned to a reviewer\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void transactionDisputeStatusFollowsCasesWithoutChangingTheFinancialStatusOrLosingHistory() throws Exception {
        var originalLedger = jdbcTemplate.queryForMap("SELECT * FROM ledger_transaction WHERE id='tx-1001'");
        var untouched = transactionActivity("tx-1001");
        assertThat(untouched.path("status").asText()).isEqualTo("BOOKED");
        assertThat(untouched.path("disputeStatus").asText()).isEqualTo("NOT_DISPUTED");
        assertThat(untouched.path("activeDisputeId").isNull()).isTrue();
        assertThat(untouched.path("latestDisputeId").isNull()).isTrue();
        assertThat(untouched.path("disputable").asBoolean()).isTrue();

        var firstCase = disputeSubmissionService.submitDispute(
                "amy", "dispute-visibility-1", createRequest("tx-1001"));
        var active = transactionActivity("tx-1001");
        assertThat(active.path("status").asText()).isEqualTo("BOOKED");
        assertThat(active.path("disputeStatus").asText()).isEqualTo("DISPUTED");
        assertThat(active.path("activeDisputeId").asText()).isEqualTo(firstCase.id());
        assertThat(active.path("latestDisputeId").asText()).isEqualTo(firstCase.id());
        assertThat(active.path("disputable").asBoolean()).isFalse();
        assertThat(active.path("amount").decimalValue()).isEqualByComparingTo(untouched.path("amount").decimalValue());
        assertThat(active.path("reference").asText()).isEqualTo(untouched.path("reference").asText());

        resolveCase(firstCase.id());
        var resolved = transactionActivity("tx-1001");
        assertThat(resolved.path("status").asText()).isEqualTo("BOOKED");
        assertThat(resolved.path("disputeStatus").asText()).isEqualTo("DISPUTE_RESOLVED");
        assertThat(resolved.path("activeDisputeId").isNull()).isTrue();
        assertThat(resolved.path("latestDisputeId").asText()).isEqualTo(firstCase.id());
        assertThat(resolved.path("disputable").asBoolean()).isTrue();
        mockMvc.perform(get("/api/disputes/" + resolved.path("latestDisputeId").asText())
                .with(user("amy").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.history.length()").value(3));

        var nextCase = disputeSubmissionService.submitDispute(
                "amy", "dispute-visibility-2", createRequest("tx-1001"));
        assertThat(nextCase.id()).isNotEqualTo(firstCase.id());
        var disputedAgain = transactionActivity("tx-1001");
        assertThat(disputedAgain.path("disputeStatus").asText()).isEqualTo("DISPUTED");
        assertThat(disputedAgain.path("activeDisputeId").asText()).isEqualTo(nextCase.id());
        assertThat(disputedAgain.path("latestDisputeId").asText()).isEqualTo(nextCase.id());
        assertThat(disputeInquiryService.getCustomerDispute("amy", firstCase.id()).history()).hasSize(3);
        assertThat(disputeInquiryService.getCustomerDisputes("amy", "", 0, 10).content()).hasSize(2);
        assertThat(jdbcTemplate.queryForMap("SELECT * FROM ledger_transaction WHERE id='tx-1001'"))
                .isEqualTo(originalLedger);
    }

    @Test
    void transactionDisputeFiltersUseAuthorizedCasesAndPaginateTheFilteredResults() throws Exception {
        disputeSubmissionService.submitDispute("amy", "filtered-active-1", createRequest("tx-1001"));
        disputeSubmissionService.submitDispute("amy", "filtered-active-2", createRequest("tx-1002"));
        var closedCase = disputeSubmissionService.submitDispute("amy", "filtered-closed-1", createRequest("tx-1003"));
        resolveCase(closedCase.id());
        disputeSubmissionService.submitDispute("ben", "filtered-other-owner", createRequest("tx-2001"));

        var firstPage = mockMvc.perform(get("/api/transactions")
                .param("disputeStatus", "DISPUTED").param("size", "1").param("page", "0")
                .with(user("amy").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].disputeStatus").value("DISPUTED")).andReturn();
        var secondPage = mockMvc.perform(get("/api/transactions")
                .param("disputeStatus", "DISPUTED").param("size", "1").param("page", "1")
                .with(user("amy").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.content.length()").value(1)).andReturn();
        String firstId = objectMapper.readTree(firstPage.getResponse().getContentAsString()).path("content").get(0).path("id").asText();
        String secondId = objectMapper.readTree(secondPage.getResponse().getContentAsString()).path("content").get(0).path("id").asText();
        assertThat(List.of(firstId, secondId)).containsExactlyInAnyOrder("tx-1001", "tx-1002");

        mockMvc.perform(get("/api/transactions").param("disputeStatus", "NOT_DISPUTED")
                .with(user("amy").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(11));
        mockMvc.perform(get("/api/transactions").param("disputeStatus", "DISPUTE_RESOLVED")
                .with(user("amy").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value("tx-1003"))
                .andExpect(jsonPath("$.content[0].latestDisputeId").value(closedCase.id()));
        mockMvc.perform(get("/api/transactions").param("disputeStatus", "DISPUTED")
                .param("accountId", "acc-everyday").param("status", "BOOKED").param("search", "WOOL-784921")
                .with(user("amy").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value("tx-1001"))
                .andExpect(jsonPath("$.content[0].status").value("BOOKED"));
        mockMvc.perform(get("/api/transactions").param("disputeStatus", "DISPUTED").param("status", "PENDING")
                .with(user("amy").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/transactions").param("disputeStatus", "DISPUTED").param("accountId", "acc-savings")
                .with(user("amy").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/transactions").param("disputeStatus", "NOT_DISPUTED").param("status", "REVERSED")
                .with(user("amy").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value("tx-1011"));
    }

    @Test
    void transactionDisputeFiltersRejectInvalidStatusesAndDoNotExposeOtherCustomersCases() throws Exception {
        var privateCase = disputeSubmissionService.submitDispute("ben", "private-filter-case", createRequest("tx-2001"));
        var amyResult = mockMvc.perform(get("/api/transactions").param("disputeStatus", "DISPUTED")
                .with(user("amy").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content.length()").value(0)).andReturn();
        assertThat(amyResult.getResponse().getContentAsString()).doesNotContain(privateCase.id(), "tx-2001");
        mockMvc.perform(get("/api/transactions").param("disputeStatus", "DISPUTED")
                .with(user("ben").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value("tx-2001"))
                .andExpect(jsonPath("$.content[0].latestDisputeId").value(privateCase.id()));
        for (String filter : List.of("NOT_DISPUTED", "DISPUTED", "DISPUTE_RESOLVED")) {
            mockMvc.perform(get("/api/transactions").param("accountId", "acc-ben").param("disputeStatus", filter)
                    .with(user("amy").roles("CUSTOMER"))).andExpect(status().isNotFound());
        }
        mockMvc.perform(get("/api/transactions").param("disputeStatus", "BOOKED")
                .with(user("amy").roles("CUSTOMER"))).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/transactions").param("status", "DISPUTED")
                .with(user("amy").roles("CUSTOMER"))).andExpect(status().isBadRequest());
    }

    @Test
    void caseListsArePagedAndSummariesRemainAuthorizedWithoutLoadingTimelines() throws Exception {
        var submitted = disputeSubmissionService.submitDispute("amy", "case-page-submitted", createRequest("tx-1001"));
        var awaiting = disputeSubmissionService.submitDispute("amy", "case-page-awaiting", createRequest("tx-1002"));
        disputeReviewService.updateStatus("reviewer", awaiting.id(),
                new DisputeStatusUpdateRequest(DisputeStatus.UNDER_REVIEW, "Investigation started", 0L));
        disputeReviewService.updateStatus("reviewer", awaiting.id(),
                new DisputeStatusUpdateRequest(DisputeStatus.AWAITING_INFORMATION, "Please confirm delivery", 1L));
        var resolved = disputeSubmissionService.submitDispute("amy", "case-page-resolved", createRequest("tx-1003"));
        resolveCase(resolved.id());
        var otherCustomer = disputeSubmissionService.submitDispute("ben", "case-page-other-owner", createRequest("tx-2001"));

        var firstResult = mockMvc.perform(get("/api/disputes").param("size", "1").param("page", "0")
                .with(user("amy").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(3)).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].historyCount").value(3))
                .andExpect(jsonPath("$.content[0].history").doesNotExist())
                .andExpect(jsonPath("$.content[0].description").doesNotExist())
                .andExpect(jsonPath("$.content[0].resolution").doesNotExist()).andReturn();
        String firstId = objectMapper.readTree(firstResult.getResponse().getContentAsString()).path("content").get(0).path("id").asText();
        var nextResult = mockMvc.perform(get("/api/disputes").param("size", "1").param("page", "1")
                .with(user("amy").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.number").value(1)).andReturn();
        assertThat(objectMapper.readTree(nextResult.getResponse().getContentAsString()).path("content").get(0).path("id").asText())
                .isNotEqualTo(firstId);
        assertThat(disputeInquiryService.getCustomerDisputes("amy", "", 0, 100).content())
                .extracting(row -> row.id()).containsExactly(resolved.id(), awaiting.id(), submitted.id())
                .doesNotContain(otherCustomer.id());
        mockMvc.perform(get("/api/disputes").param("status", "ACTIVE").with(user("amy").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/disputes").param("status", "AWAITING_INFORMATION").with(user("amy").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/disputes/summary").with(user("amy").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.active").value(2)).andExpect(jsonPath("$.awaitingInformation").value(1))
                .andExpect(jsonPath("$.resolved").value(1));
        mockMvc.perform(get("/api/staff/disputes/summary").with(user("reviewer").roles("STAFF")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.active").value(3)).andExpect(jsonPath("$.resolved").value(1));
        mockMvc.perform(get("/api/staff/disputes").param("status", "RESOLVED").param("size", "1").param("page", "1")
                .with(user("reviewer").roles("STAFF")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content.length()").value(0));
        mockMvc.perform(get("/api/disputes/" + otherCustomer.id()).with(user("amy").roles("CUSTOMER")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/staff/disputes/summary").with(user("amy").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
        for (String uri : List.of("/api/disputes?page=-1", "/api/disputes?size=101", "/api/disputes?size=0",
                "/api/disputes?status=unknown")) {
            mockMvc.perform(get(uri).with(user("amy").roles("CUSTOMER"))).andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/api/disputes?page=10001").with(user("amy").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void listQueryCountIsConstantWhenThePageGrows() {
        for (int index = 0; index < 12; index++) {
            var dispute = disputeSubmissionService.submitDispute("amy", "bounded-page-" + index, createRequest("tx-1001"));
            resolveCase(dispute.id());
        }
        var counting = new CountingDataSource(jdbcTemplate.getDataSource());
        var countedJdbc = new JdbcTemplate(counting);
        var inquiry = new DisputeInquiryService(new AccountRepository(countedJdbc),
                new DisputeCaseRepository(countedJdbc), new DisputeHistoryRepository(countedJdbc),
                new LedgerTransactionRepository(countedJdbc), transactionInquiryService);
        counting.statements.set(0);
        var oneRow = inquiry.getCustomerDisputes("amy", "", 0, 1);
        assertThat(oneRow.content()).hasSize(1);
        assertThat(counting.statements.get()).as("One-row page uses count, page, transaction batch and history-count batch").isEqualTo(4);
        counting.statements.set(0);
        var tenRows = inquiry.getCustomerDisputes("amy", "", 0, 10);
        assertThat(tenRows.content()).hasSize(10).allSatisfy(row -> assertThat(row.historyCount()).isEqualTo(3));
        assertThat(tenRows.totalElements()).isEqualTo(12);
        assertThat(counting.statements.get()).as("A larger page does not introduce per-case database calls").isEqualTo(4);
        counting.statements.set(0);
        assertThat(inquiry.getStaffDisputes("", 0, 10).content()).hasSize(10);
        assertThat(counting.statements.get()).isEqualTo(4);
    }

    @Test
    void sequenceReferencesAreUniqueAcrossConcurrentClaimsAndStableForReplay() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            var gate = new CountDownLatch(1);
            var amy = executor.submit(() -> {
                gate.await();
                return disputeSubmissionService.submitDispute("amy", "reference-amy", createRequest("tx-1001"));
            });
            var ben = executor.submit(() -> {
                gate.await();
                return disputeSubmissionService.submitDispute("ben", "reference-ben", createRequest("tx-2001"));
            });
            gate.countDown();
            var first = amy.get(10, TimeUnit.SECONDS);
            var second = ben.get(10, TimeUnit.SECONDS);
            assertThat(first.caseReference()).matches("NB-\\d{4}-\\d{12,}");
            assertThat(second.caseReference()).matches("NB-\\d{4}-\\d{12,}").isNotEqualTo(first.caseReference());
            Long allocated = jdbcTemplate.queryForObject("SELECT last_value FROM dispute_case_reference_seq", Long.class);
            assertThat(disputeSubmissionService.submitDispute("amy", "reference-amy", createRequest("tx-1001")).caseReference())
                    .isEqualTo(first.caseReference());
            assertThat(jdbcTemplate.queryForObject("SELECT last_value FROM dispute_case_reference_seq", Long.class)).isEqualTo(allocated);
            resolveCase(first.id());
            var next = disputeSubmissionService.submitDispute("amy", "reference-after-close", createRequest("tx-1001"));
            assertThat(next.caseReference()).isNotIn(first.caseReference(), second.caseReference());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void referenceMigrationKeepsExistingCasesAndStartsAboveLegacyNumericReferences() throws Exception {
        String schema = "reference_migration_" + java.util.UUID.randomUUID().toString().replace("-", "");
        String url = requiredTestSetting("TEST_DATABASE_URL");
        String username = requiredTestSetting("TEST_DATABASE_USERNAME");
        String password = requiredTestSetting("TEST_DATABASE_PASSWORD");
        try {
            Flyway.configure().dataSource(url, username, password).schemas(schema).defaultSchema(schema)
                    .target(MigrationVersion.fromVersion("1")).load().migrate();
            try (var connection = DriverManager.getConnection(url, username, password)) {
                connection.setSchema(schema);
                try (var statement = connection.createStatement()) {
                    statement.execute("""
                            INSERT INTO bank_user VALUES ('legacy','Legacy customer','test-hash','CUSTOMER');
                            INSERT INTO bank_account VALUES ('legacy-account','Account','****1234','ZAR',10.00);
                            INSERT INTO ledger_transaction VALUES ('legacy-tx','legacy-account','legacy-reference','Merchant',-1.00,
                                'ZAR',CURRENT_TIMESTAMP,'BOOKED','Shopping',CURRENT_TIMESTAMP,NULL);
                            INSERT INTO dispute_case VALUES ('legacy-case','NB-2026-000000900000','legacy-tx','legacy','UNRECOGNIZED',
                                'Historical case','RESOLVED',NULL,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'Retained resolution',2);
                            INSERT INTO dispute_history(id,dispute_id,from_status,to_status,actor,occurred_at,note)
                                VALUES ('legacy-history','legacy-case',NULL,'SUBMITTED','legacy',CURRENT_TIMESTAMP,'Retained history');
                            INSERT INTO audit_event VALUES ('legacy-audit','legacy','LEGACY_ACTION','legacy-case',CURRENT_TIMESTAMP,'Retained audit');
                            """);
                }
            }
            Flyway.configure().dataSource(url, username, password).schemas(schema).defaultSchema(schema).load().migrate();
            try (var connection = DriverManager.getConnection(url, username, password)) {
                connection.setSchema(schema);
                try (var query = connection.createStatement(); var rows = query.executeQuery("""
                        SELECT case_reference, resolution,
                               (SELECT COUNT(*) FROM dispute_history) AS history_count,
                               (SELECT COUNT(*) FROM audit_event) AS audit_count,
                               nextval('dispute_case_reference_seq') AS next_reference
                        FROM dispute_case WHERE id='legacy-case'
                        """)) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("case_reference")).isEqualTo("NB-2026-000000900000");
                    assertThat(rows.getString("resolution")).isEqualTo("Retained resolution");
                    assertThat(rows.getLong("history_count")).isEqualTo(1);
                    assertThat(rows.getLong("audit_count")).isEqualTo(1);
                    assertThat(rows.getLong("next_reference")).isGreaterThan(900000);
                }
            }
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void retentionSkipsLockedKeysAndKeepsActiveRecentAndHistoricalRecords() throws Exception {
        var expiredIds = new ArrayList<String>();
        for (int index = 1; index <= 3; index++) {
            var dispute = disputeSubmissionService.submitDispute("amy", "retention-expired-" + index, createRequest("tx-1001"));
            resolveCase(dispute.id());
            expiredIds.add(dispute.id());
        }
        var recentKey = disputeSubmissionService.submitDispute("amy", "retention-recent-key", createRequest("tx-1001"));
        resolveCase(recentKey.id());
        var recentResolution = disputeSubmissionService.submitDispute("amy", "retention-recent-resolution", createRequest("tx-1001"));
        resolveCase(recentResolution.id());
        var active = disputeSubmissionService.submitDispute("amy", "retention-old-active", createRequest("tx-1002"));
        var ancient = OffsetDateTime.now().minusDays(120);
        jdbcTemplate.update("UPDATE idempotency_record SET created_at=? WHERE request_key <> 'retention-recent-key'", ancient);
        jdbcTemplate.update("UPDATE dispute_case SET updated_at=? WHERE id <> ?", ancient, recentResolution.id());
        Long historyBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dispute_history", Long.class);
        Long auditBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_event", Long.class);
        try (var lockOwner = DriverManager.getConnection(requiredTestSetting("TEST_DATABASE_URL"),
                requiredTestSetting("TEST_DATABASE_USERNAME"), requiredTestSetting("TEST_DATABASE_PASSWORD"))) {
            lockOwner.setAutoCommit(false);
            try (var lock = lockOwner.createStatement(); var rows = lock.executeQuery("""
                    SELECT request_key FROM idempotency_record
                    WHERE username='amy' AND request_key='retention-expired-1' FOR UPDATE
                    """)) {
                assertThat(rows.next()).isTrue();
            }
            assertThat(idempotencyRetentionService.removeExpiredRecords()).isEqualTo(2);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM idempotency_record", Long.class)).isEqualTo(4);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM idempotency_record WHERE request_key='retention-expired-1'", Long.class))
                    .isEqualTo(1);
            lockOwner.rollback();
        }
        assertThat(idempotencyRetentionService.removeExpiredRecords()).isEqualTo(1);
        assertThat(idempotencyRetentionService.removeExpiredRecords()).isZero();
        assertThat(jdbcTemplate.queryForList("SELECT request_key FROM idempotency_record ORDER BY request_key", String.class))
                .containsExactly("retention-old-active", "retention-recent-key", "retention-recent-resolution");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dispute_case", Long.class)).isEqualTo(6);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dispute_history", Long.class)).isEqualTo(historyBefore);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_event", Long.class)).isEqualTo(auditBefore + 2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action='IDEMPOTENCY_RECORDS_PURGED'", Long.class)).isEqualTo(2);
        assertThat(disputeInquiryService.getCustomerDispute("amy", expiredIds.get(0)).history()).hasSize(3);
        assertThat(disputeSubmissionService.submitDispute("amy", "retention-old-active", createRequest("tx-1002")).id()).isEqualTo(active.id());
        assertThat(disputeSubmissionService.submitDispute("amy", "retention-expired-1", createRequest("tx-1001")).id())
                .isNotEqualTo(expiredIds.get(0));
        assertThat(disputeInquiryService.getCustomerDispute("amy", expiredIds.get(0)).history()).hasSize(3);
    }

    private static final class CountingDataSource extends AbstractDataSource {
        private final DataSource delegate;
        private final AtomicInteger statements = new AtomicInteger();

        private CountingDataSource(DataSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return counted(delegate.getConnection());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return counted(delegate.getConnection(username, password));
        }

        private Connection counted(Connection connection) {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("prepareStatement") || method.getName().equals("createStatement")) {
                            statements.incrementAndGet();
                        }
                        try {
                            return method.invoke(connection, arguments);
                        } catch (InvocationTargetException exception) {
                            throw exception.getCause();
                        }
                    });
        }
    }

    private JsonNode transactionActivity(String transactionId) throws Exception {
        var response = mockMvc.perform(get("/api/transactions").param("size", "100")
                .with(user("amy").roles("CUSTOMER")))
                .andExpect(status().isOk()).andReturn();
        for (JsonNode transaction : objectMapper.readTree(response.getResponse().getContentAsString()).path("content")) {
            if (transaction.path("id").asText().equals(transactionId)) {
                return transaction;
            }
        }
        throw new AssertionError("Authorized transaction was not returned: " + transactionId);
    }

    private void resolveCase(String disputeId) {
        disputeReviewService.updateStatus("reviewer", disputeId,
                new DisputeStatusUpdateRequest(DisputeStatus.UNDER_REVIEW, "Investigation started", 0L));
        disputeReviewService.updateStatus("reviewer", disputeId,
                new DisputeStatusUpdateRequest(DisputeStatus.RESOLVED, "Investigation completed", 1L));
    }

    private DisputeSubmissionRequest createRequest(String transactionId) {
        return new DisputeSubmissionRequest(transactionId, DisputeReason.UNRECOGNIZED,
                "I do not recognize this purchase.");
    }

    private String requestBody(String transactionId) throws Exception {
        return objectMapper.writeValueAsString(createRequest(transactionId));
    }

    private static final class UnknownLengthJsonRequestBuilder
            extends AbstractMockHttpServletRequestBuilder<UnknownLengthJsonRequestBuilder> {

        private UnknownLengthJsonRequestBuilder(String path) {
            super(HttpMethod.POST);
            uri(path);
        }

        @Override
        protected MockHttpServletRequest createServletRequest(ServletContext servletContext) {
            return new MockHttpServletRequest(servletContext) {
                @Override
                public int getContentLength() {
                    return -1;
                }

                @Override
                public long getContentLengthLong() {
                    return -1;
                }
            };
        }
    }
}
