import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { describe, it, expect } from 'vitest';
import { AccountInquiryClient } from './account-inquiry-client';
describe('Authorized account inquiry', () => { it('relies on the authenticated session rather than a supplied owner', async () => { TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] }); const client = TestBed.inject(AccountInquiryClient), http = TestBed.inject(HttpTestingController); const result = client.getAuthorizedAccounts(); const request = http.expectOne('/api/accounts'); expect(request.request.params.keys()).toEqual([]); request.flush([{ id: 'account-1', maskedNumber: '•••• 4829' }]); expect((await result)[0].maskedNumber).toBe('•••• 4829'); http.verify(); }); });
