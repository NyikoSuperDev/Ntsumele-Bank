import { TransactionDisputeStatus } from './transaction-activity';

export function transactionStatusLabel(value: string): string {
    return ({ BOOKED: 'Completed', PENDING: 'Pending', REVERSED: 'Reversed' } as Record<string, string>)[value] ?? value;
}
export function transactionDisputeStatusLabel(value: TransactionDisputeStatus): string {
    return { NOT_DISPUTED: 'Not disputed', DISPUTED: 'Disputed', DISPUTE_RESOLVED: 'Dispute resolved' }[value];
}
export function transactionAmount(amount: number, currency = 'ZAR'): string {
    return new Intl.NumberFormat('en-ZA', { style: 'currency', currency, currencyDisplay: 'narrowSymbol' }).format(amount);
}
export function merchantInitials(merchant: string): string {
    return merchant.split(' ').map(word => word[0]).slice(0, 2).join('').toUpperCase();
}
