import { Component, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BankIconComponent } from '../icon/bank-icon.component';
import { AfterViewInit, computed, effect, inject, Input, OnDestroy, signal } from '@angular/core';
import { DisputeCaseClient } from './dispute-case-client';
import { DisputeCase, DisputeReason, DisputeStatus } from './dispute-case';
import { DISPUTE_REASONS, disputeLabel, nextDisputeStatuses } from './dispute-display';
import { TransactionActivity } from '../transaction/transaction-activity';
import { transactionAmount, merchantInitials } from '../transaction/transaction-display';
import { httpFailureMessage } from '../http/http-failure-message';
@Component({ selector: 'nb-dispute-case-dialog', standalone: true, imports: [CommonModule, FormsModule, BankIconComponent], templateUrl: './dispute-case-dialog.component.html', styles: [':host { display: contents; }'] })
export class DisputeCaseDialogComponent implements AfterViewInit, OnDestroy {
    private readonly client = inject(DisputeCaseClient);
    readonly transaction = input<TransactionActivity | null>(null);
    readonly dispute = input<DisputeCase | null>(null);
    @Input()
    isStaff = false;
    readonly closed = output<void>();
    readonly finished = output<void>();
    readonly submitted = output<DisputeCase>();
    readonly caseChanged = output<DisputeCase>();
    readonly sessionExpired = output<void>();
    readonly selectedTransaction = computed(() => this.transaction());
    readonly selectedDispute = signal<DisputeCase | null>(null);
    readonly modal = computed(() => this.transaction() ? 'submit' : 'case');
    readonly submitting = signal(false);
    readonly modalError = signal('');
    readonly label = disputeLabel;
    readonly reasons = DISPUTE_REASONS;
    readonly nextStatuses = nextDisputeStatuses;
    readonly money = transactionAmount;
    readonly merchantInitials = merchantInitials;
    reason: DisputeReason | '' = '';
    description = '';
    consent = false;
    information = '';
    nextStatus: DisputeStatus = 'UNDER_REVIEW';
    staffNote = '';
    private idempotencyKey: string = crypto.randomUUID();
    private submittedPayload = '';
    private active = true;
    constructor() { effect(() => { const dispute = this.dispute(); this.selectedDispute.set(dispute); this.nextStatus = dispute ? (nextDisputeStatuses(dispute.status)[0] ?? 'UNDER_REVIEW') : 'UNDER_REVIEW'; }); }
    ngAfterViewInit() { queueMicrotask(() => { if (this.active)
        document.getElementById('dialog-title')?.focus(); }); }
    ngOnDestroy() { this.active = false; this.description = ''; this.information = ''; this.staffNote = ''; this.reason = ''; this.idempotencyKey = ''; this.submittedPayload = ''; this.selectedDispute.set(null); }
    closeModal() { if (!this.submitting())
        this.closed.emit(); }
    finishViewingCase() {
        if (this.active && !this.submitting() && this.modal() === 'case' && this.selectedDispute())
            this.finished.emit();
    }
    trapFocus(event: KeyboardEvent) {
        if (event.key === 'Escape') {
            this.closeModal();
            return;
        }
        if (event.key !== 'Tab')
            return;
        const elements = Array.from((event.currentTarget as HTMLElement).querySelectorAll<HTMLElement>('button:not([disabled]),[href],input:not([disabled]),select:not([disabled]),textarea:not([disabled]),[tabindex="0"]'));
        const first = elements[0], last = elements[elements.length - 1];
        if (event.shiftKey && (document.activeElement === first || document.activeElement === document.getElementById('dialog-title'))) {
            event.preventDefault();
            last?.focus();
        }
        else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first?.focus();
        }
    }
    private handleFailure(error: unknown) { if (!this.active)
        return; if ((error as {
        status?: number;
    }).status === 401)
        this.sessionExpired.emit();
    else
        this.modalError.set(httpFailureMessage(error)); }
    async submitDispute() {
        const transaction = this.transaction();
        if (!transaction || this.submitting() || !this.reason || this.description.trim().length < 20 || !this.consent)
            return;
        const request = { transactionId: transaction.id, reason: this.reason, description: this.description.trim() };
        const payload = JSON.stringify(request);
        if (this.submittedPayload && this.submittedPayload !== payload)
            this.idempotencyKey = crypto.randomUUID();
        this.submittedPayload = payload;
        this.submitting.set(true);
        this.modalError.set('');
        try {
            const result = await this.client.submitDispute(request, this.idempotencyKey);
            if (this.active)
                this.submitted.emit(result);
        }
        catch (error) {
            this.handleFailure(error);
        }
        finally {
            if (this.active)
                this.submitting.set(false);
        }
    }
    async sendInformation() {
        const dispute = this.selectedDispute();
        if (!dispute || this.submitting() || this.information.trim().length < 10)
            return;
        this.submitting.set(true);
        this.modalError.set('');
        try {
            const result = await this.client.provideInformation(dispute.id, { message: this.information.trim(), version: dispute.version });
            if (this.active) {
                this.selectedDispute.set(result);
                this.information = '';
                this.caseChanged.emit(result);
            }
        }
        catch (error) {
            this.handleFailure(error);
        }
        finally {
            if (this.active)
                this.submitting.set(false);
        }
    }
    async updateStatus() {
        const dispute = this.selectedDispute();
        if (!dispute || this.submitting() || this.staffNote.trim().length < 10)
            return;
        this.submitting.set(true);
        this.modalError.set('');
        try {
            const result = await this.client.updateReviewStatus(dispute.id, { status: this.nextStatus, note: this.staffNote.trim(), version: dispute.version });
            if (this.active) {
                this.selectedDispute.set(result);
                this.staffNote = '';
                this.nextStatus = nextDisputeStatuses(result.status)[0] ?? 'UNDER_REVIEW';
                this.caseChanged.emit(result);
            }
        }
        catch (error) {
            this.handleFailure(error);
        }
        finally {
            if (this.active)
                this.submitting.set(false);
        }
    }
}
