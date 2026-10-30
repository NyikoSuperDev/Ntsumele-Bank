import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { BankingWorkspaceComponent } from './banking-workspace.component';
import { SessionAccessClient } from '../identity/session-access-client';
import { AccountInquiryClient } from '../account/account-inquiry-client';
import { TransactionActivityClient } from '../transaction/transaction-activity-client';
import { DisputeCaseClient } from '../dispute/dispute-case-client';
import { TransactionActivity, TransactionPage } from '../transaction/transaction-activity';
import { DisputeCase, DisputeCounts, DisputePage, DisputeSearchRequest, DisputeSummary } from '../dispute/dispute-case';
import { AccountSummary } from '../account/account-summary';

const transaction: TransactionActivity = {
    id: 'tx-finished', accountId: 'acc-everyday', merchant: 'Finished merchant', reference: 'FINISHED-REF',
    amount: -20, currency: 'ZAR', bookedAt: '2026-10-29T06:00:00Z', category: 'Shopping',
    status: 'BOOKED', disputable: true, activeDisputeId: null, latestDisputeId: null, disputeStatus: 'NOT_DISPUTED'
};
const submittedCase: DisputeCase = {
    id: 'finished-case', caseReference: 'NB-2026-000000010483', reason: 'UNRECOGNIZED',
    description: 'I do not recognise this transaction.', status: 'SUBMITTED', version: 0,
    createdAt: '2026-10-29T06:00:00Z', updatedAt: '2026-10-29T06:00:00Z',
    transaction: { ...transaction, disputable: false, activeDisputeId: 'finished-case', latestDisputeId: 'finished-case', disputeStatus: 'DISPUTED' },
    history: [{ id: 'finished-history', fromStatus: null, toStatus: 'SUBMITTED', actor: 'amy', occurredAt: '2026-10-29T06:00:00Z', note: 'Case submitted.' }]
};
const caseSummary: DisputeSummary = {
    id: submittedCase.id, caseReference: submittedCase.caseReference, transaction: submittedCase.transaction,
    reason: submittedCase.reason, status: submittedCase.status, createdAt: submittedCase.createdAt,
    updatedAt: submittedCase.updatedAt, version: submittedCase.version, historyCount: submittedCase.history.length
};
const account: AccountSummary = { id: 'acc-everyday', name: 'Everyday', maskedNumber: '••••4829', balance: 100, currency: 'ZAR' };
const transactionPage = (id = transaction.id, number = 0): TransactionPage => ({ content: [{ ...transaction, id }], number, size: 8, totalElements: 24, totalPages: 3 });
const casePage = (rows: DisputeSummary[] = [], number = 0, total = rows.length, size = 10): DisputePage => ({ content: rows, number, size, totalElements: total, totalPages: Math.ceil(total / size) });
const counts: DisputeCounts = { total: 55, active: 40, awaitingInformation: 7, resolved: 15 };

describe('Banking feature composition and session boundaries', () => {
    let component: BankingWorkspaceComponent;
    let activity: { searchTransactions: ReturnType<typeof vi.fn> };
    let sessions: { signOut: ReturnType<typeof vi.fn> };
    let cases: {
        getCustomerCase: ReturnType<typeof vi.fn>; getCustomerCases: ReturnType<typeof vi.fn>;
        getReviewCase: ReturnType<typeof vi.fn>; getReviewCases: ReturnType<typeof vi.fn>;
        getCustomerSummary: ReturnType<typeof vi.fn>; getReviewSummary: ReturnType<typeof vi.fn>;
        submitDispute: ReturnType<typeof vi.fn>; provideInformation: ReturnType<typeof vi.fn>; updateReviewStatus: ReturnType<typeof vi.fn>;
    };
    let accountInquiry: { getAuthorizedAccounts: ReturnType<typeof vi.fn> };
    beforeEach(() => {
        activity = { searchTransactions: vi.fn().mockResolvedValue(transactionPage()) };
        sessions = { signOut: vi.fn() };
        const empty = (request: DisputeSearchRequest) => Promise.resolve(casePage([], request.page, 0, request.size));
        cases = {
            getCustomerCase: vi.fn(), getCustomerCases: vi.fn().mockImplementation(empty),
            getReviewCase: vi.fn(), getReviewCases: vi.fn().mockImplementation(empty),
            getCustomerSummary: vi.fn().mockResolvedValue(counts), getReviewSummary: vi.fn().mockResolvedValue(counts),
            submitDispute: vi.fn(), provideInformation: vi.fn(), updateReviewStatus: vi.fn()
        };
        accountInquiry = { getAuthorizedAccounts: vi.fn().mockResolvedValue([account]) };
        TestBed.configureTestingModule({ imports: [BankingWorkspaceComponent], providers: [
            { provide: SessionAccessClient, useValue: sessions }, { provide: AccountInquiryClient, useValue: accountInquiry },
            { provide: TransactionActivityClient, useValue: activity }, { provide: DisputeCaseClient, useValue: cases }
        ] });
        component = TestBed.createComponent(BankingWorkspaceComponent).componentInstance;
        component.user.set({ username: 'amy', displayName: 'Amy Mokoena', role: 'CUSTOMER' });
    });
    it('ignores an earlier account response arriving after a newer selection', async () => {
        let resolveFirst!: (value: TransactionPage) => void;
        activity.searchTransactions.mockImplementationOnce(() => new Promise<TransactionPage>(resolve => resolveFirst = resolve)).mockResolvedValueOnce(transactionPage('second-account'));
        component.accountId = 'first'; const first = component.loadTransactions();
        component.accountId = 'second'; await component.loadTransactions();
        resolveFirst(transactionPage('first-account')); await first;
        expect(component.transactions().content[0].id).toBe('second-account'); expect(component.loading()).toBe(false);
    });
    it('clears customer state on sign-out and suppresses a late ledger response', async () => {
        let resolveLedger!: (value: TransactionPage) => void;
        activity.searchTransactions.mockImplementation(() => new Promise<TransactionPage>(resolve => resolveLedger = resolve));
        sessions.signOut.mockResolvedValue(undefined); component.accountId = 'first'; component.search = 'private reference';
        component.transactionDisputeStatus = 'DISPUTED'; component.openDispute(transaction);
        const pending = component.loadTransactions(); await component.signOut(); resolveLedger(transactionPage()); await pending;
        expect(component.user()).toBeNull(); expect(component.accounts()).toEqual([]); expect(component.disputes()).toEqual([]);
        expect(component.disputeCounts().total).toBe(0); expect(component.transactions().content).toEqual([]);
        expect(component.search).toBe(''); expect(component.transactionDisputeStatus).toBe(''); expect(component.modal()).toBeNull();
        expect(component.selectedTransaction()).toBeNull(); expect(component.view()).toBe('transactions');
    });
    it('forwards and retains both transaction filters during workspace searches', async () => {
        activity.searchTransactions.mockResolvedValue(transactionPage('filtered', 1));
        await component.searchTransactions({ accountId: 'acc-everyday', page: 1, search: ' Coffee ', status: 'BOOKED', disputeStatus: 'DISPUTE_RESOLVED' });
        expect(activity.searchTransactions).toHaveBeenLastCalledWith({ accountId: 'acc-everyday', page: 1, search: 'Coffee', status: 'BOOKED', disputeStatus: 'DISPUTE_RESOLVED' });
        expect(component.transactionDisputeStatus).toBe('DISPUTE_RESOLVED');
    });
    it('returns to sign-in and closes all feature details on session expiry', async () => {
        activity.searchTransactions.mockRejectedValue({ status: 401 }); component.accountId = 'first'; component.modal.set('submit');
        await component.loadTransactions(); expect(component.user()).toBeNull(); expect(component.modal()).toBeNull();
        expect(component.error()).toContain('sign in'); expect(component.transactions().content).toEqual([]);
    });
    it('does not reopen a case after the customer signs out', async () => {
        let resolveCase!: (value: DisputeCase) => void;
        cases.getCustomerCase.mockImplementation(() => new Promise<DisputeCase>(resolve => resolveCase = resolve));
        sessions.signOut.mockResolvedValue(undefined); const pending = component.openCaseById('private-case');
        await component.signOut(); resolveCase(submittedCase); await pending;
        expect(component.user()).toBeNull(); expect(component.modal()).toBeNull(); expect(component.selectedDispute()).toBeNull();
    });
    it('renders the transaction feature and opens an immutable transaction in the dispute feature', () => {
        const fixture = TestBed.createComponent(BankingWorkspaceComponent), workspace = fixture.componentInstance;
        vi.spyOn(workspace, 'ngOnInit').mockResolvedValue(undefined); workspace.booting.set(false);
        workspace.user.set({ username: 'amy', displayName: 'Amy Mokoena', role: 'CUSTOMER' }); workspace.accountId = account.id;
        workspace.accounts.set([account]); workspace.transactions.set(transactionPage()); fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('nb-transaction-activity table')).toBeTruthy();
        fixture.nativeElement.querySelector('.transaction-action').click(); fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('nb-dispute-case-dialog [role="dialog"]')).toBeTruthy();
        expect(fixture.nativeElement.querySelector('.transaction-receipt').textContent).toContain('FINISHED-REF');
    });
    it('closes Finished immediately, refreshes aggregate and page state, and preserves account/view/filters/page', async () => {
        let resolveAccounts!: (value: AccountSummary[]) => void;
        accountInquiry.getAuthorizedAccounts.mockImplementation(() => new Promise<AccountSummary[]>(resolve => resolveAccounts = resolve));
        cases.getCustomerCases.mockImplementation((request: DisputeSearchRequest) => Promise.resolve(casePage([caseSummary], request.page, 31, request.size)));
        const refreshedTransactions = { ...transactionPage(), content: [submittedCase.transaction] };
        activity.searchTransactions.mockResolvedValue(refreshedTransactions);
        const fixture = TestBed.createComponent(BankingWorkspaceComponent), workspace = fixture.componentInstance;
        vi.spyOn(workspace, 'ngOnInit').mockResolvedValue(undefined); const refresh = vi.spyOn(workspace, 'loadWorkspace');
        workspace.booting.set(false); workspace.user.set({ username: 'amy', displayName: 'Amy Mokoena', role: 'CUSTOMER' });
        workspace.accountId = account.id; workspace.search = 'Finished merchant'; workspace.transactionStatus = 'BOOKED';
        workspace.transactionDisputeStatus = 'DISPUTED'; workspace.historyFilter = 'active'; workspace.disputePage.set(casePage([], 2, 31));
        workspace.accounts.set([account]); workspace.transactions.set(transactionPage()); workspace.openCase(submittedCase); fixture.detectChanges();
        const root = fixture.nativeElement as HTMLElement;
        Array.from(root.querySelectorAll<HTMLButtonElement>('[role="dialog"] button')).find(button => button.textContent?.trim() === 'Finished')!.click();
        fixture.detectChanges(); expect(root.querySelector('[role="dialog"]')).toBeNull(); expect(workspace.loading()).toBe(true);
        const refreshedAccounts = [{ ...account, balance: 275.50 }]; resolveAccounts(refreshedAccounts); await refresh.mock.results[0].value; fixture.detectChanges();
        expect(accountInquiry.getAuthorizedAccounts).toHaveBeenCalledOnce(); expect(cases.getCustomerSummary).toHaveBeenCalledOnce();
        expect(cases.getCustomerCases.mock.calls).toEqual([[{ page: 0, size: 3, status: 'ACTIVE' }], [{ page: 2, size: 10, status: 'ACTIVE' }]]);
        expect(activity.searchTransactions).toHaveBeenCalledExactlyOnceWith({ accountId: account.id, page: 0, search: 'Finished merchant', status: 'BOOKED', disputeStatus: 'DISPUTED' });
        expect(workspace.accounts()).toEqual(refreshedAccounts); expect(workspace.transactions()).toEqual(refreshedTransactions);
        expect(workspace.disputes()).toEqual([caseSummary]); expect(workspace.disputePage().number).toBe(2); expect(workspace.disputeCounts()).toEqual(counts);
        expect(workspace.view()).toBe('transactions'); expect(workspace.accountId).toBe(account.id); expect(workspace.historyFilter).toBe('active'); expect(workspace.loading()).toBe(false);
        expect(root.querySelector('.balance-strip strong')?.textContent).toContain('275'); expect(root.querySelector('.transaction-dispute-badge')?.textContent).toContain('Disputed');
        expect(root.querySelector('.summary-card strong')?.textContent).toContain('40');
        expect(cases.submitDispute).not.toHaveBeenCalled(); expect(cases.provideInformation).not.toHaveBeenCalled(); expect(cases.updateReviewStatus).not.toHaveBeenCalled();
        expect(submittedCase.status).toBe('SUBMITTED');
    });
    it('clears customer state if the Finished refresh discovers an expired session', async () => {
        accountInquiry.getAuthorizedAccounts.mockRejectedValue({ status: 401 }); component.accountId = account.id;
        component.accounts.set([account]); component.transactions.set(transactionPage()); component.disputePage.set(casePage([caseSummary])); component.openCase(submittedCase);
        await component.finishViewingCase(); expect(component.user()).toBeNull(); expect(component.modal()).toBeNull(); expect(component.accounts()).toEqual([]);
        expect(component.transactions().content).toEqual([]); expect(component.disputes()).toEqual([]); expect(component.disputeCounts().total).toBe(0);
        expect(component.error()).toContain('sign in'); expect(activity.searchTransactions).not.toHaveBeenCalled();
    });
    it('fetches one bounded list page and a three-case preview while summary totals remain independent of the page', async () => {
        cases.getCustomerCases.mockImplementation((request: DisputeSearchRequest) => Promise.resolve(casePage([caseSummary], request.page, 55, request.size)));
        await component.loadWorkspace();
        expect(cases.getCustomerCases.mock.calls).toEqual([[{ page: 0, size: 3, status: 'ACTIVE' }], [{ page: 0, size: 10, status: '' }]]);
        expect(component.disputes()).toHaveLength(1); expect(component.disputePage().totalElements).toBe(55); expect(component.disputeCounts().active).toBe(40);
        expect(component.activePreview()).toEqual([caseSummary]); expect(cases.getCustomerCase).not.toHaveBeenCalled(); expect(cases.getReviewCase).not.toHaveBeenCalled();
    });
    it('uses server filters and rejects a late history page after a newer filter has loaded', async () => {
        let resolveFirst!: (value: DisputePage) => void;
        cases.getCustomerCases.mockImplementationOnce(() => new Promise<DisputePage>(resolve => resolveFirst = resolve))
            .mockResolvedValueOnce(casePage([{ ...caseSummary, status: 'RESOLVED' }], 0));
        component.historyFilter = 'active'; const pending = component.loadHistory(2);
        await component.selectHistoryFilter('resolved'); resolveFirst(casePage([caseSummary], 2, 31)); await pending;
        expect(cases.getCustomerCases.mock.calls).toEqual([[{ page: 2, size: 10, status: 'ACTIVE' }], [{ page: 0, size: 10, status: 'RESOLVED' }]]);
        expect(component.disputes()[0].status).toBe('RESOLVED'); expect(component.disputePage().number).toBe(0); expect(component.historyLoading()).toBe(false);
    });
    it('clamps an emptied final page once after changes instead of downloading earlier pages', async () => {
        cases.getCustomerCases.mockResolvedValueOnce(casePage([], 4, 12)).mockResolvedValueOnce(casePage([caseSummary], 1, 12));
        component.historyFilter = 'active'; await component.loadHistory(4);
        expect(cases.getCustomerCases.mock.calls).toEqual([[{ page: 4, size: 10, status: 'ACTIVE' }], [{ page: 1, size: 10, status: 'ACTIVE' }]]);
        expect(component.disputePage().number).toBe(1);
    });
    it('loads staff summaries and detail from staff routes without retrieving customer accounts or every case', async () => {
        component.user.set({ username: 'reviewer', displayName: 'Review team', role: 'STAFF' });
        cases.getReviewCases.mockResolvedValue(casePage([caseSummary], 0, 55)); cases.getReviewCase.mockResolvedValue(submittedCase);
        await component.loadWorkspace(); expect(cases.getReviewSummary).toHaveBeenCalledOnce();
        expect(cases.getReviewCases).toHaveBeenCalledExactlyOnceWith({ page: 0, size: 10, status: '' });
        expect(accountInquiry.getAuthorizedAccounts).not.toHaveBeenCalled(); expect(cases.getCustomerCases).not.toHaveBeenCalled();
        await component.openCaseById(caseSummary.id); expect(cases.getReviewCase).toHaveBeenCalledExactlyOnceWith(caseSummary.id);
        expect(cases.getCustomerCase).not.toHaveBeenCalled(); expect(component.selectedDispute()?.history).toHaveLength(1);
    });
    it('discards a late history page and workspace summary after sign-out', async () => {
        let resolvePage!: (value: DisputePage) => void, resolveCounts!: (value: DisputeCounts) => void;
        cases.getCustomerCases.mockImplementation((request: DisputeSearchRequest) => request.size === 3
            ? Promise.resolve(casePage([caseSummary], 0, 31, 3)) : new Promise<DisputePage>(resolve => resolvePage = resolve));
        const history = component.loadHistory();
        cases.getCustomerSummary.mockImplementation(() => new Promise<DisputeCounts>(resolve => resolveCounts = resolve));
        const workspace = component.loadWorkspace(); sessions.signOut.mockResolvedValue(undefined); await component.signOut();
        resolvePage(casePage([caseSummary])); resolveCounts(counts); await Promise.all([history, workspace]);
        expect(component.disputes()).toEqual([]); expect(component.disputeCounts().total).toBe(0); expect(component.activePreview()).toEqual([]);
        expect(component.user()).toBeNull(); expect(component.loading()).toBe(false);
    });
});
