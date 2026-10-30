import { inject, Injectable } from '@angular/core';
import { HttpHeaders, HttpParams } from '@angular/common/http';
import { SessionHttpClient } from '../http/session-http-client';
import { DisputeCase, DisputeCounts, DisputeInformationRequest, DisputePage, DisputeSearchRequest, DisputeStatusUpdateRequest, DisputeSubmissionRequest } from './dispute-case';
@Injectable({ providedIn: 'root' })
export class DisputeCaseClient {
    private readonly http = inject(SessionHttpClient);
    getCustomerCases(request: DisputeSearchRequest = { page: 0, size: 10, status: '' }): Promise<DisputePage> {
        return this.http.get('/api/disputes', this.pageParameters(request));
    }
    getReviewCases(request: DisputeSearchRequest = { page: 0, size: 10, status: '' }): Promise<DisputePage> {
        return this.http.get('/api/staff/disputes', this.pageParameters(request));
    }
    getCustomerSummary(): Promise<DisputeCounts> { return this.http.get('/api/disputes/summary'); }
    getReviewSummary(): Promise<DisputeCounts> { return this.http.get('/api/staff/disputes/summary'); }
    getCustomerCase(id: string): Promise<DisputeCase> { return this.http.get('/api/disputes/' + encodeURIComponent(id)); }
    getReviewCase(id: string): Promise<DisputeCase> { return this.http.get('/api/staff/disputes/' + encodeURIComponent(id)); }
    private pageParameters(request: DisputeSearchRequest): HttpParams {
        return new HttpParams().set('page', request.page).set('size', request.size).set('status', request.status);
    }
    submitDispute(request: DisputeSubmissionRequest, idempotencyKey: string): Promise<DisputeCase> {
        const body: DisputeSubmissionRequest = {
            transactionId: request.transactionId, reason: request.reason, description: request.description
        };
        return this.http.post('/api/disputes', body, new HttpHeaders({ 'Idempotency-Key': idempotencyKey }));
    }
    provideInformation(id: string, request: DisputeInformationRequest): Promise<DisputeCase> {
        return this.http.post('/api/disputes/' + encodeURIComponent(id) + '/information', { message: request.message, version: request.version });
    }
    updateReviewStatus(id: string, request: DisputeStatusUpdateRequest): Promise<DisputeCase> {
        return this.http.post('/api/staff/disputes/' + encodeURIComponent(id) + '/status', {
            status: request.status, note: request.note, version: request.version
        });
    }
}
