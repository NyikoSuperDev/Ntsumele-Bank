import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { DisputeCaseDialogComponent } from './dispute-case-dialog.component';
import { DisputeCaseClient } from './dispute-case-client';
import { DisputeCase } from './dispute-case';
import { TransactionActivity } from '../transaction/transaction-activity';
describe('Dispute dialog intent and draft lifecycle', () => {
    let client: {
        submitDispute: ReturnType<typeof vi.fn>;
    };
    const transaction: TransactionActivity = { accountId: 'acc-everyday', status: 'BOOKED', category: 'Shopping', disputable: true, activeDisputeId: null, latestDisputeId: null, disputeStatus: 'NOT_DISPUTED', id: 'tx-1', merchant: 'Merchant', reference: 'REF-1', amount: -20, currency: 'ZAR', bookedAt: '2026-10-29T06:00:00Z' };
    const submittedCase: DisputeCase = {
        id: 'case-1', caseReference: 'NB-2026-TEST', reason: 'UNRECOGNIZED',
        description: 'I do not recognise this transaction.', status: 'SUBMITTED', version: 0,
        transaction: { ...transaction, disputable: false, activeDisputeId: 'case-1', latestDisputeId: 'case-1', disputeStatus: 'DISPUTED' },
        createdAt: '2026-10-29T06:00:00Z', updatedAt: '2026-10-29T06:00:00Z',
        history: [{ id: 'history-1', fromStatus: null, toStatus: 'SUBMITTED', actor: 'amy', occurredAt: '2026-10-29T06:00:00Z', note: 'Customer submitted a case.' }]
    };
    beforeEach(() => { client = { submitDispute: vi.fn() }; TestBed.configureTestingModule({ imports: [DisputeCaseDialogComponent], providers: [{ provide: DisputeCaseClient, useValue: client }] }); });
    function dialog() { const fixture = TestBed.createComponent(DisputeCaseDialogComponent); fixture.componentRef.setInput('transaction', transaction); fixture.detectChanges(); const component = fixture.componentInstance; component.reason = 'UNRECOGNIZED'; component.description = 'I do not recognise this transaction.'; component.consent = true; return { fixture, component }; }
    it('retains its idempotency key after an uncertain failure and sends no financial values', async () => { client.submitDispute.mockRejectedValueOnce({ status: 0 }).mockResolvedValueOnce({ id: 'case-1' }); const { component } = dialog(); await component.submitDispute(); await component.submitDispute(); expect(client.submitDispute).toHaveBeenCalledTimes(2); const first = client.submitDispute.mock.calls[0], second = client.submitDispute.mock.calls[1]; expect(first[1]).toBe(second[1]); expect(first[0]).toEqual({ transactionId: 'tx-1', reason: 'UNRECOGNIZED', description: 'I do not recognise this transaction.' }); expect(transaction.amount).toBe(-20); });
    it('erases drafts and ignores a late submission response after destruction', async () => { let resolveSubmission!: (value: DisputeCase) => void; client.submitDispute.mockImplementation(() => new Promise<DisputeCase>(resolve => resolveSubmission = resolve)); const { fixture, component } = dialog(); const submitted = vi.fn(); component.submitted.subscribe(submitted); const pending = component.submitDispute(); fixture.destroy(); resolveSubmission({ id: 'private-case' } as DisputeCase); await pending; expect(component.description).toBe(''); expect(component.reason).toBe(''); expect(component.selectedDispute()).toBeNull(); expect(submitted).not.toHaveBeenCalled(); });
    it('blocks closing during a write and allows Escape once idle', () => { const { component } = dialog(); const closed = vi.fn(); component.closed.subscribe(closed); component.submitting.set(true); component.trapFocus(new KeyboardEvent('keydown', { key: 'Escape' })); expect(closed).not.toHaveBeenCalled(); component.submitting.set(false); component.trapFocus(new KeyboardEvent('keydown', { key: 'Escape' })); expect(closed).toHaveBeenCalledOnce(); });
    it('offers Finished only while viewing a case and leaves the case lifecycle unchanged', () => {
        const draft = dialog();
        const draftRoot = draft.fixture.nativeElement as HTMLElement;
        expect(Array.from(draftRoot.querySelectorAll('button')).some(button => button.textContent?.trim() === 'Finished')).toBe(false);
        draft.fixture.destroy();

        const fixture = TestBed.createComponent(DisputeCaseDialogComponent);
        fixture.componentRef.setInput('dispute', submittedCase);
        fixture.detectChanges();
        const finished = vi.fn(), closed = vi.fn(), changed = vi.fn();
        fixture.componentInstance.finished.subscribe(finished);
        fixture.componentInstance.closed.subscribe(closed);
        fixture.componentInstance.caseChanged.subscribe(changed);
        const root = fixture.nativeElement as HTMLElement;
        const button = Array.from(root.querySelectorAll('button')).find(button => button.textContent?.trim() === 'Finished');
        expect(button).toBeTruthy();
        expect(button?.type).toBe('button');
        button!.click();
        expect(finished).toHaveBeenCalledOnce();
        expect(closed).not.toHaveBeenCalled();
        expect(changed).not.toHaveBeenCalled();
        expect(client.submitDispute).not.toHaveBeenCalled();
        expect(submittedCase.status).toBe('SUBMITTED');
        expect(submittedCase.version).toBe(0);
    });
    it('disables Finished during a pending write and permits finishing once idle', () => {
        const fixture = TestBed.createComponent(DisputeCaseDialogComponent);
        fixture.componentRef.setInput('dispute', submittedCase);
        fixture.detectChanges();
        const component = fixture.componentInstance, finished = vi.fn();
        component.finished.subscribe(finished);
        component.submitting.set(true);
        fixture.detectChanges();
        const root = fixture.nativeElement as HTMLElement;
        const button = Array.from(root.querySelectorAll('button')).find(button => button.textContent?.trim() === 'Finished')!;
        expect(button.disabled).toBe(true);
        button.click();
        component.finishViewingCase();
        expect(finished).not.toHaveBeenCalled();
        component.submitting.set(false);
        fixture.detectChanges();
        expect(button.disabled).toBe(false);
        button.click();
        expect(finished).toHaveBeenCalledOnce();
    });
});
