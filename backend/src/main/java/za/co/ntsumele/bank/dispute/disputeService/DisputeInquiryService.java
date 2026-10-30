package za.co.ntsumele.bank.dispute.disputeService;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.ntsumele.bank.account.accountRepository.AccountRepository;
import za.co.ntsumele.bank.dispute.disputeDto.DisputeCaseResponse;
import za.co.ntsumele.bank.dispute.disputeDto.DisputeCountsResponse;
import za.co.ntsumele.bank.dispute.disputeDto.DisputePageResponse;
import za.co.ntsumele.bank.dispute.disputeDto.DisputeSummaryResponse;
import za.co.ntsumele.bank.dispute.disputeModel.DisputeCaseCounts;
import za.co.ntsumele.bank.dispute.disputeModel.DisputeSearchResult;
import za.co.ntsumele.bank.dispute.disputeMapper.DisputeResponseMapper;
import za.co.ntsumele.bank.dispute.disputeModel.DisputeCase;
import za.co.ntsumele.bank.dispute.disputeModel.DisputeStatus;
import za.co.ntsumele.bank.dispute.disputeRepository.DisputeCaseRepository;
import za.co.ntsumele.bank.dispute.disputeRepository.DisputeHistoryRepository;
import za.co.ntsumele.bank.failure.failureException.BankingRuleViolation;
import za.co.ntsumele.bank.transaction.transactionModel.LedgerTransaction;
import za.co.ntsumele.bank.transaction.transactionRepository.LedgerTransactionRepository;
import za.co.ntsumele.bank.transaction.transactionService.TransactionInquiryService;

/**
 * class : DisputeInquiryService
 * Author : Nyiko Chauke
 * 2026
 *
 * Retrieves authorized, paginated case summaries using bounded batched queries and server-side totals.
 * Loads complete timelines only for individual case details and supplies workspace-wide aggregate counts.
 */
@Service
@Transactional(readOnly = true)
public class DisputeInquiryService {

    private final AccountRepository accountRepository;
    private final DisputeCaseRepository disputeCaseRepository;
    private final DisputeHistoryRepository disputeHistoryRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final TransactionInquiryService transactionInquiryService;

    public DisputeInquiryService(
            AccountRepository accountRepository,
            DisputeCaseRepository disputeCaseRepository,
            DisputeHistoryRepository disputeHistoryRepository,
            LedgerTransactionRepository ledgerTransactionRepository,
            TransactionInquiryService transactionInquiryService) {
        this.accountRepository = accountRepository;
        this.disputeCaseRepository = disputeCaseRepository;
        this.disputeHistoryRepository = disputeHistoryRepository;
        this.ledgerTransactionRepository = ledgerTransactionRepository;
        this.transactionInquiryService = transactionInquiryService;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DisputePageResponse getCustomerDisputes(String username, String status, int page, int size) {
        validatePage(status, page, size);
        return toPage(disputeCaseRepository.findAuthorizedPage(username, status, page, size));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DisputePageResponse getStaffDisputes(String status, int page, int size) {
        validatePage(status, page, size);
        return toPage(disputeCaseRepository.findStaffPage(status, page, size));
    }

    public DisputeCountsResponse getCustomerCounts(String username) {
        return toCounts(disputeCaseRepository.countAuthorizedCases(username));
    }

    public DisputeCountsResponse getStaffCounts() {
        return toCounts(disputeCaseRepository.countStaffCases());
    }

    private DisputeCountsResponse toCounts(DisputeCaseCounts counts) {
        return new DisputeCountsResponse(counts.total(), counts.active(), counts.awaitingInformation(), counts.resolved());
    }

    private DisputePageResponse toPage(DisputeSearchResult page) {
        if (page.content().isEmpty()) {
            return new DisputePageResponse(List.of(), page.totalElements(), page.totalPages(), page.number(), page.size());
        }
        Map<String, LedgerTransaction> transactions = ledgerTransactionRepository.findAllByIds(
                        page.content().stream().map(DisputeCase::transactionId).toList()).stream()
                .collect(Collectors.toMap(LedgerTransaction::id, Function.identity()));
        Map<String, Long> historyCounts = disputeHistoryRepository.countByDisputeIds(
                page.content().stream().map(DisputeCase::id).toList());
        List<DisputeSummaryResponse> rows = page.content().stream().map(dispute -> {
            LedgerTransaction transaction = transactions.get(dispute.transactionId());
            if (transaction == null) {
                throw new IllegalStateException("A dispute references a missing ledger transaction.");
            }
            return new DisputeSummaryResponse(dispute.id(), dispute.caseReference(),
                    transactionInquiryService.toResponse(transaction), dispute.reason(), dispute.status(),
                    dispute.createdAt(), dispute.updatedAt(), dispute.version(), historyCounts.getOrDefault(dispute.id(), 0L));
        }).toList();
        return new DisputePageResponse(rows, page.totalElements(), page.totalPages(), page.number(), page.size());
    }

    public DisputeCaseResponse getCustomerDispute(String username, String disputeId) {
        if (!accountRepository.hasDisputeAccess(username, disputeId)) {
            throw BankingRuleViolation.notFound();
        }
        return toResponse(findCase(disputeId));
    }

    public DisputeCaseResponse getStaffDispute(String disputeId) {
        return toResponse(findCase(disputeId));
    }

    DisputeCase findCase(String disputeId) {
        return disputeCaseRepository.findById(disputeId).orElseThrow(BankingRuleViolation::notFound);
    }

    DisputeCaseResponse toResponse(DisputeCase disputeCase) {
        LedgerTransaction transaction = ledgerTransactionRepository.findById(disputeCase.transactionId())
                .orElseThrow(BankingRuleViolation::notFound);
        return DisputeResponseMapper.toResponse(disputeCase, transactionInquiryService.toResponse(transaction),
                disputeHistoryRepository.findByDisputeId(disputeCase.id()));
    }

    private void validatePage(String status, int page, int size) {
        if (status == null || page < 0 || size < 1 || size > 100) {
            throw BankingRuleViolation.badRequest("INVALID_REQUEST", "Check the request fields and try again.");
        }
        if (!status.isBlank() && !"ACTIVE".equals(status)) {
            try {
                DisputeStatus.valueOf(status);
            } catch (IllegalArgumentException exception) {
                throw BankingRuleViolation.badRequest("INVALID_REQUEST", "Check the request fields and try again.");
            }
        }
    }
}
