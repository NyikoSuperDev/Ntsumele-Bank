package za.co.ntsumele.bank.dispute.disputeModel;

/**
 * class : DisputeReason
 * Author : Nyiko Chauke
 * 2026
 *
 * Defines the supported reasons for opening a dispute: unrecognized payments, duplicate charges,
 * incorrect amounts and goods not received.
 */
public enum DisputeReason {
    UNRECOGNIZED,
    DUPLICATE,
    INCORRECT_AMOUNT,
    GOODS_NOT_RECEIVED
}
