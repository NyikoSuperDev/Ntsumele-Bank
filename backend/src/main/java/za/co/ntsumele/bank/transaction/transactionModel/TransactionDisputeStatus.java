package za.co.ntsumele.bank.transaction.transactionModel;

/**
 * class : TransactionDisputeStatus
 * Author : Nyiko Chauke
 * 2026
 *
 * Defines whether a transaction has never been disputed, has an active case, or has only
 * resolved case history. These values classify cases independently of the ledger payment status.
 */
public enum TransactionDisputeStatus {
    NOT_DISPUTED,
    DISPUTED,
    DISPUTE_RESOLVED
}
