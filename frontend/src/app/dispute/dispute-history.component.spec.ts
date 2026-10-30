import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { DisputeHistoryComponent } from './dispute-history.component';
import { DisputePage, DisputeSummary } from './dispute-case';

const summary: DisputeSummary = {
    id: 'case-paged', caseReference: 'NB-2026-000000010483', reason: 'UNRECOGNIZED', status: 'SUBMITTED',
    createdAt: '2026-10-29T06:00:00Z', updatedAt: '2026-10-29T06:00:00Z', version: 0, historyCount: 1200,
    transaction: { id: 'tx-paged', accountId: 'acc-everyday', merchant: 'Paged merchant', reference: 'PAGED-REF',
        amount: -20, currency: 'ZAR', bookedAt: '2026-10-29T06:00:00Z', status: 'BOOKED', category: 'Shopping',
        disputable: false, activeDisputeId: 'case-paged', latestDisputeId: 'case-paged', disputeStatus: 'DISPUTED' }
};

describe('Server-paginated dispute history', () => {
    beforeEach(() => TestBed.configureTestingModule({ imports: [DisputeHistoryComponent] }));
    function history() {
        const fixture = TestBed.createComponent(DisputeHistoryComponent);
        const page: DisputePage = { content: [summary], number: 2, size: 10, totalElements: 45, totalPages: 5 };
        fixture.componentRef.setInput('page', page); fixture.detectChanges();
        return { fixture, component: fixture.componentInstance, root: fixture.nativeElement as HTMLElement };
    }
    it('renders summary history counts without timelines and emits only the selected ID/page', () => {
        const { component, root } = history(), selected = vi.fn(), paged = vi.fn();
        component.caseSelected.subscribe(selected); component.pageChanged.subscribe(paged);
        expect(root.textContent).toContain('1200 updates'); expect(root.textContent).toContain('of 45 cases');
        expect(root.textContent).toContain('Page 3 of 5'); expect(root.querySelector('.timeline')).toBeNull();
        root.querySelector<HTMLButtonElement>('.dispute-row')!.click();
        root.querySelector<HTMLButtonElement>('[aria-label="Next dispute page"]')!.click();
        expect(selected).toHaveBeenCalledExactlyOnceWith('case-paged'); expect(paged).toHaveBeenCalledExactlyOnceWith(3);
    });
    it('delegates filtering to the server and disables page/filter actions during a read', () => {
        const { fixture, component, root } = history(), filtered = vi.fn();
        component.historyFilterChange.subscribe(filtered);
        Array.from(root.querySelectorAll<HTMLButtonElement>('.segmented button')).find(button => button.textContent === 'Resolved')!.click();
        expect(filtered).toHaveBeenCalledExactlyOnceWith('resolved');
        fixture.componentRef.setInput('loading', true); fixture.detectChanges();
        expect(root.querySelector<HTMLButtonElement>('[aria-label="Next dispute page"]')!.disabled).toBe(true);
        expect(Array.from(root.querySelectorAll<HTMLButtonElement>('.segmented button')).every(button => button.disabled)).toBe(true);
    });
});
