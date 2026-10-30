import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TransactionActivityComponent } from './transaction-activity.component';
import { TransactionActivity, TransactionPage } from './transaction-activity';

const transaction = (id: string, values: Partial<TransactionActivity> = {}): TransactionActivity => ({
    id, accountId: 'acc-everyday', merchant: `Merchant ${id}`, reference: `REF-${id}`,
    amount: -20, currency: 'ZAR', bookedAt: '2026-10-29T06:00:00Z', observedAt: '2026-10-29T06:00:00Z',
    category: 'Shopping', status: 'BOOKED', disputable: true, activeDisputeId: null,
    latestDisputeId: null, disputeStatus: 'NOT_DISPUTED', ...values
});

describe('Transaction payment and dispute status', () => {
    beforeEach(() => TestBed.configureTestingModule({ imports: [TransactionActivityComponent] }));

    function activity() {
        const fixture = TestBed.createComponent(TransactionActivityComponent);
        fixture.componentRef.setInput('accounts', [{ id: 'acc-everyday', name: 'Everyday', maskedNumber: '••••4829', balance: 100, currency: 'ZAR' }]);
        fixture.componentInstance.accountId = 'acc-everyday';
        const content = [
            transaction('never'),
            transaction('active', { disputable: false, activeDisputeId: 'active-case', latestDisputeId: 'active-case', disputeStatus: 'DISPUTED' }),
            transaction('resolved', { latestDisputeId: 'closed-case', disputeStatus: 'DISPUTE_RESOLVED' }),
            transaction('pending', { status: 'PENDING', disputable: false })
        ];
        const page: TransactionPage = { content, number: 0, size: 8, totalElements: 20, totalPages: 3 };
        fixture.componentRef.setInput('transactions', page);
        fixture.detectChanges();
        return { fixture, component: fixture.componentInstance, content };
    }

    it('keeps settlement unchanged while distinguishing never disputed, active and resolved history', () => {
        const { fixture } = activity();
        const rows = [...fixture.nativeElement.querySelectorAll('tbody tr')] as HTMLTableRowElement[];
        expect([...fixture.nativeElement.querySelectorAll('th')].map((cell: Element) => cell.textContent?.trim()))
            .toContain('PAYMENT STATUS');
        expect([...fixture.nativeElement.querySelectorAll('th')].map((cell: Element) => cell.textContent?.trim()))
            .toContain('DISPUTE STATUS');
        expect(rows.map(row => row.querySelector('.payment-status-badge')?.textContent?.trim()))
            .toEqual(['Completed', 'Completed', 'Completed', 'Pending']);
        expect(rows.map(row => row.querySelector('.transaction-dispute-badge')?.textContent?.trim()))
            .toEqual(['Not disputed', 'Disputed', 'Dispute resolved', 'Not disputed']);
        expect(rows[1].querySelector('.transaction-action')?.textContent).toContain('View dispute');
        expect(rows[1].querySelectorAll('.transaction-action')).toHaveLength(1);
        expect(rows[3].querySelector('.not-eligible')?.textContent).toContain('Awaiting settlement');
    });

    it('keeps a closed case accessible while permitting a separate new claim only when eligible', () => {
        const { fixture, component, content } = activity();
        const caseSelected = vi.fn(), disputeRequested = vi.fn();
        component.caseSelected.subscribe(caseSelected);
        component.disputeRequested.subscribe(disputeRequested);
        const root = fixture.nativeElement as HTMLElement;
        root.querySelector<HTMLButtonElement>('[aria-label="View dispute for Merchant active"]')!.click();
        root.querySelector<HTMLButtonElement>('[aria-label="View dispute history for Merchant resolved"]')!.click();
        root.querySelector<HTMLButtonElement>('[aria-label="Dispute again: Merchant resolved"]')!.click();
        expect(caseSelected.mock.calls).toEqual([['active-case'], ['closed-case']]);
        expect(disputeRequested).toHaveBeenCalledExactlyOnceWith(content[2]);
        expect(content[2].status).toBe('BOOKED');
        expect(content[2].disputeStatus).toBe('DISPUTE_RESOLVED');
        fixture.componentRef.setInput('transactions', { content: [{ ...content[2], disputable: false }], number: 0, size: 8, totalElements: 1, totalPages: 1 });
        fixture.detectChanges();
        expect(root.querySelector('[aria-label="Dispute again: Merchant resolved"]')).toBeNull();
        expect(root.querySelector('[aria-label="View dispute history for Merchant resolved"]')).toBeTruthy();
    });

    it('combines both filters on the server, resets the page on change and retains them when paging', async () => {
        const { fixture, component } = activity();
        await fixture.whenStable();
        const changed = vi.fn();
        component.queryChanged.subscribe(changed);
        const payment = fixture.nativeElement.querySelector('#transaction-status') as HTMLSelectElement;
        payment.value = 'BOOKED';
        payment.dispatchEvent(new Event('change'));
        await fixture.whenStable();
        const dispute = fixture.nativeElement.querySelector('#transaction-dispute-status') as HTMLSelectElement;
        dispute.value = 'DISPUTE_RESOLVED';
        dispute.dispatchEvent(new Event('change'));
        await fixture.whenStable();
        fixture.detectChanges();
        expect(changed).toHaveBeenLastCalledWith({ accountId: 'acc-everyday', page: 0, search: '', status: 'BOOKED', disputeStatus: 'DISPUTE_RESOLVED' });
        fixture.nativeElement.querySelector('[aria-label="Next page"]').click();
        expect(changed).toHaveBeenLastCalledWith({ accountId: 'acc-everyday', page: 1, search: '', status: 'BOOKED', disputeStatus: 'DISPUTE_RESOLVED' });
    });
});
