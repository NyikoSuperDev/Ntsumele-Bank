import { inject, Injectable } from '@angular/core';
import { HttpHeaders, HttpParams } from '@angular/common/http';
import { SessionHttpClient } from '../http/session-http-client';
import { SessionIdentity, SessionSignInRequest } from './session-identity';
@Injectable({ providedIn: 'root' })
export class SessionAccessClient {
    private readonly http = inject(SessionHttpClient);
    getIdentity(): Promise<SessionIdentity> { return this.http.get('/api/me'); }
    signIn(request: SessionSignInRequest): Promise<void> {
        const body = new HttpParams().set('username', request.username).set('password', request.password);
        return this.http.post('/api/login', body, new HttpHeaders({ 'Content-Type': 'application/x-www-form-urlencoded' }));
    }
    signOut(): Promise<void> { return this.http.post('/api/logout', {}); }
}
