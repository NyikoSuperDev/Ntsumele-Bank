import { TransactionActivity } from '../transaction/transaction-activity';
export type DisputeStatus = 'SUBMITTED' | 'UNDER_REVIEW' | 'AWAITING_INFORMATION' | 'RESOLVED';
export type DisputeReason = 'UNRECOGNIZED' | 'DUPLICATE' | 'INCORRECT_AMOUNT' | 'GOODS_NOT_RECEIVED';
export interface DisputeHistoryEvent {
    id: string;
    fromStatus: string | null;
    toStatus: DisputeStatus;
    actor: string;
    occurredAt: string;
    note: string;
}
export interface DisputeCase {
    id: string;
    caseReference: string;
    transaction: TransactionActivity;
    reason: DisputeReason;
    description: string;
    status: DisputeStatus;
    createdAt: string;
    updatedAt: string;
    resolution?: string;
    version: number;
    history: DisputeHistoryEvent[];
}
export interface DisputeSummary {
    id: string;
    caseReference: string;
    transaction: TransactionActivity;
    reason: DisputeReason;
    status: DisputeStatus;
    createdAt: string;
    updatedAt: string;
    version: number;
    historyCount: number;
}
export interface DisputePage {
    content: DisputeSummary[];
    number: number;
    size: number;
    totalElements: number;
    totalPages: number;
}
export interface DisputeCounts {
    total: number;
    active: number;
    awaitingInformation: number;
    resolved: number;
}
export interface DisputeSearchRequest {
    page: number;
    size: number;
    status: '' | 'ACTIVE' | DisputeStatus;
}
export interface DisputeSubmissionRequest {
    transactionId: string;
    reason: DisputeReason;
    description: string;
}
export interface DisputeInformationRequest {
    message: string;
    version: number;
}
export interface DisputeStatusUpdateRequest {
    status: DisputeStatus;
    note: string;
    version: number;
}
