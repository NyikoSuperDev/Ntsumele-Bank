import { Component, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BankIconComponent } from '../icon/bank-icon.component';
import { Input } from '@angular/core';
import { DisputePage } from './dispute-case';
import { disputeLabel } from './dispute-display';
import { transactionAmount, merchantInitials } from '../transaction/transaction-display';
export type DisputeHistoryFilter = 'all' | 'active' | 'awaiting' | 'resolved';
@Component({ selector: 'nb-dispute-history', standalone: true, imports: [CommonModule, BankIconComponent], templateUrl: './dispute-history.component.html', styles: [':host { display: contents; }'] })
export class DisputeHistoryComponent {
    readonly page = input<DisputePage>({ content: [], number: 0, size: 10, totalElements: 0, totalPages: 0 });
    readonly loading = input(false);
    readonly caseSelected = output<string>();
    readonly pageChanged = output<number>();
    readonly viewTransactions = output<void>();
    readonly historyFilterChange = output<DisputeHistoryFilter>();
    @Input()
    historyFilter: DisputeHistoryFilter = 'all';
    @Input()
    isStaff = false;
    readonly label = disputeLabel;
    readonly money = transactionAmount;
    readonly merchantInitials = merchantInitials;
    selectFilter(filter: DisputeHistoryFilter) {
        if (!this.loading()) this.historyFilterChange.emit(filter);
    }
}
