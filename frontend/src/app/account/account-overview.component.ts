import { Component, input, output } from '@angular/core';
import { BankIconComponent } from '../icon/bank-icon.component';
import { AccountSummary } from './account-summary';
@Component({ selector: 'nb-account-overview', standalone: true, imports: [BankIconComponent], templateUrl: './account-overview.component.html', styles: [':host { display: contents; }'] })
export class AccountOverviewComponent {
    readonly accounts = input.required<AccountSummary[]>();
    readonly accountSelected = output<string>();
    readonly viewTransactions = output<void>();
    money(amount: number, currency = 'ZAR') { return new Intl.NumberFormat('en-ZA', { style: 'currency', currency, currencyDisplay: 'narrowSymbol' }).format(amount); }
}
