import { TestBed } from '@angular/core/testing';
import { provideHttpClient, HttpParams } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { SessionAccessClient } from './session-access-client';
describe('Session access contract', () => {
    let client: SessionAccessClient;
    let http: HttpTestingController;
    beforeEach(() => { TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] }); client = TestBed.inject(SessionAccessClient); http = TestBed.inject(HttpTestingController); });
    afterEach(() => http.verify());
    it('fetches CSRF before form sign-in and keeps credentials out of the URL', async () => {
        const result = client.signIn({ username: 'amy', password: 'test-password' });
        http.expectOne('/api/csrf').flush({ token: 'csrf-value', headerName: 'X-CSRF-TOKEN' });
        const request = http.expectOne('/api/login');
        expect(request.request.method).toBe('POST');
        expect(request.request.headers.get('X-CSRF-TOKEN')).toBe('csrf-value');
        expect(request.request.headers.get('Content-Type')).toBe('application/x-www-form-urlencoded');
        expect(request.request.body).toBeInstanceOf(HttpParams);
        expect(request.request.body.get('username')).toBe('amy');
        expect(request.request.body.get('password')).toBe('test-password');
        expect(request.request.params.keys()).toEqual([]);
        request.flush(null);
        await result;
    });
    it('obtains a fresh token for sign-out', async () => { const result = client.signOut(); http.expectOne('/api/csrf').flush({ token: 'new-token', headerName: 'X-CSRF-TOKEN' }); const request = http.expectOne('/api/logout'); expect(request.request.headers.get('X-CSRF-TOKEN')).toBe('new-token'); request.flush(null); await result; });
});
