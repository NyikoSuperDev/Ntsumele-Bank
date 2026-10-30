import { DisputeStatus } from './dispute-case';
export const DISPUTE_REASONS = [
    { value: 'UNRECOGNIZED', label: "I don't recognise this transaction" },
    { value: 'DUPLICATE', label: 'I was charged more than once' },
    { value: 'INCORRECT_AMOUNT', label: 'The amount is incorrect' },
    { value: 'GOODS_NOT_RECEIVED', label: 'Goods or services not received' }
];
export function disputeLabel(value: string | null | undefined): string {
    if (!value)
        return '—';
    const labels: Record<string, string> = {
        SUBMITTED: 'Submitted', UNDER_REVIEW: 'Under review', AWAITING_INFORMATION: 'Awaiting information', RESOLVED: 'Resolved'
    };
    return labels[value] ?? DISPUTE_REASONS.find(reason => reason.value === value)?.label ?? value;
}
export function nextDisputeStatuses(status: DisputeStatus): DisputeStatus[] {
    switch (status) {
        case 'SUBMITTED': return ['UNDER_REVIEW'];
        case 'UNDER_REVIEW': return ['AWAITING_INFORMATION', 'RESOLVED'];
        case 'AWAITING_INFORMATION': return ['UNDER_REVIEW', 'RESOLVED'];
        case 'RESOLVED': return [];
    }
}
