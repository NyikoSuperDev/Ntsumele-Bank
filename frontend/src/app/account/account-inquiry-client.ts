import { inject, Injectable } from '@angular/core';
import { SessionHttpClient } from '../http/session-http-client';
import { AccountSummary } from './account-summary';
@Injectable({ providedIn: 'root' })
export class AccountInquiryClient {
    private readonly http = inject(SessionHttpClient);
    getAuthorizedAccounts(): Promise<AccountSummary[]> { return this.http.get('/api/accounts'); }
}
