package za.co.ntsumele.bank.dispute.disputeModel;

/**
 * class : DisputeStatus
 * Author : Nyiko Chauke
 * 2026
 *
 * Defines the lifecycle states of a dispute case, independently of the ledger payment status.
 * The review and information services enforce which transitions between these states are permitted.
 */
public enum DisputeStatus {
    SUBMITTED,
    UNDER_REVIEW,
    AWAITING_INFORMATION,
    RESOLVED
}
