import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { SessionAccessClient } from '../identity/session-access-client';
import { SessionIdentity, SessionSignInRequest } from '../identity/session-identity';
import { BankSignInComponent } from '../identity/bank-sign-in.component';
import { AccountInquiryClient } from '../account/account-inquiry-client';
import { AccountSummary } from '../account/account-summary';
import { AccountOverviewComponent } from '../account/account-overview.component';
import { TransactionActivityClient } from '../transaction/transaction-activity-client';
import { TransactionActivity, TransactionDisputeStatus, TransactionPage, TransactionSearchRequest } from '../transaction/transaction-activity';
import { TransactionActivityComponent } from '../transaction/transaction-activity.component';
import { DisputeCaseClient } from '../dispute/dispute-case-client';
import { DisputeCase, DisputeCounts, DisputePage, DisputeSearchRequest, DisputeSummary } from '../dispute/dispute-case';
import { DisputeHistoryComponent, DisputeHistoryFilter } from '../dispute/dispute-history.component';
import { DisputeHistoryPreviewComponent } from '../dispute/dispute-history-preview.component';
import { DisputeCaseDialogComponent } from '../dispute/dispute-case-dialog.component';
import { BankIconComponent } from '../icon/bank-icon.component';
import { httpFailureMessage } from '../http/http-failure-message';

const emptyCasePage = (): DisputePage => ({ content: [], number: 0, size: 10, totalElements: 0, totalPages: 0 });
const emptyCounts = (): DisputeCounts => ({ total: 0, active: 0, awaitingInformation: 0, resolved: 0 });

/** Composes bounded feature queries; case details are loaded only when opened. */
@Component({ selector: 'nb-root', standalone: true, imports: [BankSignInComponent, AccountOverviewComponent, TransactionActivityComponent, DisputeHistoryComponent, DisputeHistoryPreviewComponent, DisputeCaseDialogComponent, BankIconComponent], templateUrl: './banking-workspace.component.html' })
export class BankingWorkspaceComponent implements OnInit {
    private readonly sessions = inject(SessionAccessClient);
    private readonly accountInquiry = inject(AccountInquiryClient);
    private readonly activity = inject(TransactionActivityClient);
    private readonly cases = inject(DisputeCaseClient);
    readonly user = signal<SessionIdentity | null>(null);
    readonly booting = signal(true);
    readonly busy = signal(false);
    readonly accounts = signal<AccountSummary[]>([]);
    readonly transactions = signal<TransactionPage>({ content: [], number: 0, size: 8, totalElements: 0, totalPages: 0 });
    readonly disputePage = signal<DisputePage>(emptyCasePage());
    readonly disputes = computed(() => this.disputePage().content);
    readonly disputeCounts = signal<DisputeCounts>(emptyCounts());
    readonly activePreview = signal<DisputeSummary[]>([]);
    readonly historyLoading = signal(false);
    private readonly workspaceLoading = signal(false);
    private readonly transactionLoading = signal(false);
    readonly loading = computed(() => this.workspaceLoading() || this.historyLoading() || this.transactionLoading());
    readonly error = signal('');
    readonly notice = signal('');
    readonly selectedTransaction = signal<TransactionActivity | null>(null);
    readonly selectedDispute = signal<DisputeCase | null>(null);
    readonly supportOpen = signal(false);
    readonly mobileNav = signal(false);
    readonly view = signal<'overview' | 'transactions' | 'history'>('transactions');
    readonly modal = signal<'submit' | 'case' | null>(null);
    accountId = '';
    search = '';
    transactionStatus = '';
    transactionDisputeStatus: TransactionDisputeStatus | '' = '';
    historyFilter: DisputeHistoryFilter = 'all';
    private lastFocused: HTMLElement | null = null;
    private transactionRequest = 0;
    private historyRequest = 0;
    private workspaceRequest = 0;
    private caseRequest = 0;
    private sessionGeneration = 0;
    get isStaff() { return this.user()?.role === 'STAFF'; }
    get initials() { return this.user()?.displayName.split(' ').filter(Boolean).map(word => word[0]).slice(0, 2).join('') ?? ''; }

    async ngOnInit() {
        try { this.user.set(await this.sessions.getIdentity()); await this.loadWorkspace(); }
        catch (error) { if ((error as { status?: number }).status !== 401) this.error.set(httpFailureMessage(error)); }
        finally { this.booting.set(false); }
    }
    async signIn(request: SessionSignInRequest) {
        if (this.busy()) return;
        this.busy.set(true); this.error.set('');
        try { await this.sessions.signIn(request); this.user.set(await this.sessions.getIdentity()); await this.loadWorkspace(); }
        catch (error) { this.error.set((error as { status?: number }).status === 401 ? 'Those sign-in details were not recognised. Please try again.' : httpFailureMessage(error)); }
        finally { this.busy.set(false); }
    }
    async signOut() {
        if (this.busy()) return;
        this.busy.set(true); this.error.set('');
        try { await this.sessions.signOut(); this.resetSession(); }
        catch (error) { this.handleFailure(error); }
        finally { this.busy.set(false); }
    }
    async loadWorkspace() {
        const generation = this.sessionGeneration, request = ++this.workspaceRequest, staff = this.isStaff;
        this.workspaceLoading.set(true); this.error.set('');
        try {
            const [accounts, counts, preview] = await Promise.all([
                staff ? Promise.resolve([] as AccountSummary[]) : this.accountInquiry.getAuthorizedAccounts(),
                staff ? this.cases.getReviewSummary() : this.cases.getCustomerSummary(),
                staff ? Promise.resolve(null) : this.cases.getCustomerCases({ page: 0, size: 3, status: 'ACTIVE' })
            ]);
            if (generation !== this.sessionGeneration || request !== this.workspaceRequest) return;
            this.disputeCounts.set(counts);
            this.activePreview.set(preview?.content ?? []);
            if (staff) this.view.set('history');
            else {
                this.accounts.set(accounts);
                if (!accounts.some(account => account.id === this.accountId)) this.accountId = accounts[0]?.id ?? '';
            }
            await Promise.all([this.loadHistory(this.disputePage().number), staff ? Promise.resolve() : this.loadTransactions(this.transactions().number)]);
        } catch (error) {
            if (generation === this.sessionGeneration && request === this.workspaceRequest) this.handleFailure(error);
        } finally {
            if (request === this.workspaceRequest) this.workspaceLoading.set(false);
        }
    }
    async loadHistory(page = 0) {
        if (!this.user()) return;
        const generation = this.sessionGeneration, request = ++this.historyRequest, staff = this.isStaff;
        const criteria: DisputeSearchRequest = { page, size: 10, status: ({ all: '', active: 'ACTIVE', awaiting: 'AWAITING_INFORMATION', resolved: 'RESOLVED' } as const)[this.historyFilter] };
        const query = (index: number) => staff ? this.cases.getReviewCases({ ...criteria, page: index }) : this.cases.getCustomerCases({ ...criteria, page: index });
        this.historyLoading.set(true); this.error.set('');
        try {
            let result = await query(page);
            if (generation !== this.sessionGeneration || request !== this.historyRequest) return;
            const lastPage = Math.max(0, result.totalPages - 1);
            if (page > lastPage) {
                result = await query(lastPage);
                if (generation !== this.sessionGeneration || request !== this.historyRequest) return;
            }
            this.disputePage.set(result);
        } catch (error) {
            if (generation === this.sessionGeneration && request === this.historyRequest) this.handleFailure(error);
        } finally {
            if (request === this.historyRequest) this.historyLoading.set(false);
        }
    }
    async selectHistoryFilter(filter: DisputeHistoryFilter) {
        this.historyFilter = filter;
        await this.loadHistory(0);
    }
    async showHistory(filter: DisputeHistoryFilter) {
        this.view.set('history'); this.mobileNav.set(false); this.notice.set('');
        await this.selectHistoryFilter(filter);
    }
    async loadTransactions(page = 0) {
        if (!this.accountId) return;
        const request = ++this.transactionRequest, generation = this.sessionGeneration;
        const criteria = { accountId: this.accountId, search: this.search.trim(), status: this.transactionStatus, disputeStatus: this.transactionDisputeStatus };
        const query = (index: number) => this.activity.searchTransactions({ ...criteria, page: index });
        this.transactionLoading.set(true); this.error.set('');
        try {
            let result = await query(page);
            if (request !== this.transactionRequest || generation !== this.sessionGeneration) return;
            if (page > Math.max(0, result.totalPages - 1)) {
                result = await query(Math.max(0, result.totalPages - 1));
                if (request !== this.transactionRequest || generation !== this.sessionGeneration) return;
            }
            this.transactions.set(result);
        } catch (error) {
            if (request === this.transactionRequest && generation === this.sessionGeneration) this.handleFailure(error);
        } finally {
            if (request === this.transactionRequest) this.transactionLoading.set(false);
        }
    }
    async searchTransactions(request: TransactionSearchRequest) {
        this.accountId = request.accountId; this.search = request.search; this.transactionStatus = request.status; this.transactionDisputeStatus = request.disputeStatus;
        await this.loadTransactions(request.page);
    }
    async selectAccount(accountId: string) { this.accountId = accountId; this.go('transactions'); await this.loadTransactions(); }
    go(view: 'overview' | 'transactions' | 'history') {
        const previous = this.view();
        this.view.set(view); this.mobileNav.set(false); this.notice.set('');
        if (view === 'history' && previous !== 'history') void this.loadHistory(this.disputePage().number);
    }
    async refresh() { await this.loadWorkspace(); }
    private handleFailure(error: unknown) {
        this.error.set(httpFailureMessage(error));
        if ((error as { status?: number }).status === 401) this.resetSession();
    }
    expireSession() { this.error.set('Your session has ended. Please sign in again.'); this.resetSession(); }
    private resetSession() {
        this.transactionRequest++; this.historyRequest++; this.workspaceRequest++; this.caseRequest++; this.sessionGeneration++;
        this.user.set(null); this.accounts.set([]); this.disputePage.set(emptyCasePage()); this.disputeCounts.set(emptyCounts()); this.activePreview.set([]);
        this.transactions.set({ content: [], number: 0, size: 8, totalElements: 0, totalPages: 0 });
        this.accountId = ''; this.search = ''; this.transactionStatus = ''; this.transactionDisputeStatus = ''; this.historyFilter = 'all';
        this.lastFocused = null; this.modal.set(null); this.selectedTransaction.set(null); this.selectedDispute.set(null);
        this.notice.set(''); this.supportOpen.set(false); this.mobileNav.set(false); this.view.set('transactions');
        this.workspaceLoading.set(false); this.historyLoading.set(false); this.transactionLoading.set(false);
    }
    openDispute(transaction: TransactionActivity) {
        this.caseRequest++; this.lastFocused = document.activeElement as HTMLElement;
        this.selectedDispute.set(null); this.selectedTransaction.set(transaction); this.modal.set('submit');
    }
    openCase(dispute: DisputeCase) {
        this.lastFocused = document.activeElement as HTMLElement;
        this.selectedTransaction.set(null); this.selectedDispute.set(dispute); this.modal.set('case');
    }
    async openCaseById(id: string) {
        if (!this.user()) return;
        const generation = this.sessionGeneration, request = ++this.caseRequest;
        try {
            const dispute = await (this.isStaff ? this.cases.getReviewCase(id) : this.cases.getCustomerCase(id));
            if (generation === this.sessionGeneration && request === this.caseRequest) this.openCase(dispute);
        } catch (error) {
            if (generation === this.sessionGeneration && request === this.caseRequest) this.handleFailure(error);
        }
    }
    closeModal() { this.caseRequest++; this.modal.set(null); this.selectedTransaction.set(null); this.selectedDispute.set(null); this.lastFocused?.focus(); }
    async finishViewingCase() {
        if (this.modal() !== 'case' || !this.user()) return;
        this.closeModal(); await this.loadWorkspace();
    }
    async onCaseSubmitted(dispute: DisputeCase) {
        const generation = this.sessionGeneration;
        this.closeModal(); await this.loadWorkspace();
        if (generation !== this.sessionGeneration || !this.user()) return;
        this.notice.set(`Dispute ${dispute.caseReference} submitted. You can follow every update in Dispute history.`);
        this.view.set('history'); this.openCase(dispute);
    }
    async onCaseChanged(dispute: DisputeCase) {
        const generation = this.sessionGeneration;
        this.selectedDispute.set(dispute); await this.loadWorkspace();
        if (generation === this.sessionGeneration && this.user()) this.notice.set('The case has been updated and recorded in its history.');
    }
}
