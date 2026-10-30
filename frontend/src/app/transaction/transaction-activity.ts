export type TransactionDisputeStatus = 'NOT_DISPUTED' | 'DISPUTED' | 'DISPUTE_RESOLVED';

export interface TransactionActivity {
    id: string;
    accountId: string;
    merchant: string;
    reference: string;
    amount: number;
    currency: string;
    bookedAt: string;
    observedAt?: string;
    status: 'BOOKED' | 'PENDING' | 'REVERSED';
    category: string;
    disputable: boolean;
    activeDisputeId: string | null;
    latestDisputeId: string | null;
    disputeStatus: TransactionDisputeStatus;
}
export interface TransactionPage {
    content: TransactionActivity[];
    totalElements: number;
    totalPages: number;
    number: number;
    size: number;
}
export interface TransactionSearchRequest {
    accountId: string;
    page: number;
    search: string;
    status: string;
    disputeStatus: TransactionDisputeStatus | '';
}
