import { Component, input, output } from '@angular/core';
import { BankIconComponent } from '../icon/bank-icon.component';
import { DisputeSummary } from './dispute-case';
import { disputeLabel } from './dispute-display';
import { merchantInitials } from '../transaction/transaction-display';
@Component({ selector: 'nb-dispute-history-preview', standalone: true, imports: [BankIconComponent], templateUrl: './dispute-history-preview.component.html', styles: [':host { display: contents; }'] })
export class DisputeHistoryPreviewComponent {
    readonly cases = input<DisputeSummary[]>([]);
    readonly caseSelected = output<string>();
    readonly viewAll = output<void>();
    readonly label = disputeLabel;
    readonly merchantInitials = merchantInitials;
    get activeDisputes() { return this.cases(); }
}
