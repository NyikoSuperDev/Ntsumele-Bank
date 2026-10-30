import { describe, it, expect } from 'vitest';
import { httpFailureMessage } from './http-failure-message';
describe('Session-safe failure messages', () => {
    it('explains expired sessions and lost connectivity', () => { expect(httpFailureMessage({ status: 401 })).toContain('sign in'); expect(httpFailureMessage({ status: 0 })).toContain('connection'); });
    it('preserves actionable server conflicts and explains uncertain timeouts', () => { expect(httpFailureMessage({ status: 409, error: { message: 'Case has changed' } })).toBe('Case has changed'); expect(httpFailureMessage({ name: 'TimeoutError' })).toContain('repeated dispute'); });
});
