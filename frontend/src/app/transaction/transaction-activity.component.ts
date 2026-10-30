import { Component, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BankIconComponent } from '../icon/bank-icon.component';
import { Input } from '@angular/core';
import { AccountSummary } from '../account/account-summary';
import { TransactionActivity, TransactionDisputeStatus, TransactionPage, TransactionSearchRequest } from './transaction-activity';
import { transactionAmount, transactionStatusLabel, transactionDisputeStatusLabel, merchantInitials } from './transaction-display';
@Component({ selector: 'nb-transaction-activity', standalone: true, imports: [CommonModule, FormsModule, BankIconComponent], templateUrl: './transaction-activity.component.html', styles: [':host { display: contents; }'] })
export class TransactionActivityComponent {
    readonly accounts = input.required<AccountSummary[]>();
    readonly transactions = input.required<TransactionPage>();
    readonly loading = input(false);
    readonly queryChanged = output<TransactionSearchRequest>();
    readonly disputeRequested = output<TransactionActivity>();
    readonly caseSelected = output<string>();
    readonly helpRequested = output<void>();
    @Input()
    accountId = '';
    @Input()
    search = '';
    @Input()
    transactionStatus = '';
    @Input()
    disputeStatus: TransactionDisputeStatus | '' = '';
    readonly money = transactionAmount;
    readonly label = transactionStatusLabel;
    readonly disputeLabel = transactionDisputeStatusLabel;
    readonly merchantInitials = merchantInitials;
    get account() { return this.accounts().find(account => account.id === this.accountId); }
    loadTransactions(page = 0) { this.queryChanged.emit({ accountId: this.accountId, page, search: this.search.trim(), status: this.transactionStatus, disputeStatus: this.disputeStatus }); }
}
