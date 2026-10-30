# Payment status and dispute status

Account activity shows two independent statuses. A dispute case never changes the payment's original banking status or financial values.

| Payment status | Meaning |
| --- | --- |
| Completed (`BOOKED`) | The ledger reports a booked payment |
| Pending (`PENDING`) | The payment is awaiting settlement |
| Reversed (`REVERSED`) | The ledger reports a reversal |

| Dispute status | Rule | Available actions |
| --- | --- | --- |
| Not disputed (`NOT_DISPUTED`) | The transaction has never had a dispute case | Dispute, when eligible |
| Disputed (`DISPUTED`) | There is an active case: Submitted, Under review or Awaiting information | View dispute |
| Dispute resolved (`DISPUTE_RESOLVED`) | Previous cases exist and none is active | View history; Dispute again when eligible under the existing policy |

A resolved case does not turn a transaction back into **Not disputed**. Its history remains available. If a new eligible case is submitted after resolution, the transaction returns to **Disputed**; all earlier cases remain in Dispute history. Resolution records an investigation outcome and does not by itself post a refund or change the ledger amount.

The filters for Payment status and Dispute status can be combined with account selection, merchant/reference search and pagination. Filtering happens on the server after account authorization, so totals and pages reflect the selected criteria. A **Not disputed** badge does not imply eligibility: pending payments, reversals, credits and stale ledger observations remain subject to the existing rules.

The response adds `disputeStatus` and `latestDisputeId` while retaining `status` and `activeDisputeId`. The case linked by `latestDisputeId` is the active case when present, otherwise the most recently created case, ordered deterministically by creation time and ID. Case details enforce the same account-access checks as before.

These values are derived from existing dispute records. There is no additional mutable status field in the ledger and no database migration for this change. The more detailed case lifecycle remains Submitted → Under review → Awaiting information → Resolved, with the existing permitted return transitions and optimistic version checks.

Choose **Finished** in the case-details popup to close it and refresh the workspace data. The current page/view, account and filters are preserved, and the latest payment/dispute states are fetched from the server. The button is disabled during a case submission or update; it does not itself change a case status.
