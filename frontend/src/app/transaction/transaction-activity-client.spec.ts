import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { describe, it, expect } from 'vitest';
import { TransactionActivityClient } from './transaction-activity-client';

describe('Transaction activity query', () => {
    it('sends independent payment/dispute filters with the account and bounded page', async () => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        const client = TestBed.inject(TransactionActivityClient);
        const http = TestBed.inject(HttpTestingController);
        const result = client.searchTransactions({ accountId: 'acc-everyday', page: 2, search: 'Coffee', status: 'BOOKED', disputeStatus: 'DISPUTED' });
        const request = http.expectOne(r => r.url === '/api/transactions');
        expect(Object.fromEntries(request.request.params.keys().map(key => [key, request.request.params.get(key)])))
            .toEqual({ accountId: 'acc-everyday', page: '2', size: '8', search: 'Coffee', status: 'BOOKED', disputeStatus: 'DISPUTED' });
        request.flush({ content: [], number: 2, size: 8, totalElements: 0, totalPages: 0 });
        await result;
        http.verify();
    });
});
