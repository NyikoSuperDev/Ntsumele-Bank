import { inject, Injectable } from '@angular/core';
import { HttpParams } from '@angular/common/http';
import { SessionHttpClient } from '../http/session-http-client';
import { TransactionPage, TransactionSearchRequest } from './transaction-activity';
@Injectable({ providedIn: 'root' })
export class TransactionActivityClient {
    private readonly http = inject(SessionHttpClient);
    searchTransactions(request: TransactionSearchRequest): Promise<TransactionPage> {
        const params = new HttpParams().set('accountId', request.accountId).set('page', request.page)
            .set('size', 8).set('search', request.search).set('status', request.status)
            .set('disputeStatus', request.disputeStatus);
        return this.http.get('/api/transactions', params);
    }
}
