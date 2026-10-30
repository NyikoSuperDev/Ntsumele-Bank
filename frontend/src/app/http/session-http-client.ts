import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { firstValueFrom, switchMap, timeout } from 'rxjs';
/** Session-bound HTTP protocol: timed reads and fresh CSRF tokens for every mutation. */
@Injectable({ providedIn: 'root' })
export class SessionHttpClient {
    private readonly http = inject(HttpClient);
    get<Response>(path: string, params?: HttpParams): Promise<Response> {
        return firstValueFrom(this.http.get<Response>(path, { params }).pipe(timeout(15000)));
    }
    post<Response>(path: string, body: unknown, headers = new HttpHeaders()): Promise<Response> {
        return firstValueFrom(this.http.get<{
            token: string;
            headerName: string;
        }>('/api/csrf').pipe(switchMap(csrf => this.http.post<Response>(path, body, { headers: headers.set(csrf.headerName, csrf.token) })), timeout(15000)));
    }
}
