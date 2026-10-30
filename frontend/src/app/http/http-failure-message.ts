export function httpFailureMessage(error: unknown, fallback = 'Something went wrong. Please try again.'): string {
    const e = (error ?? {}) as {
        status?: number;
        name?: string;
        error?: {
            message?: string;
        };
    };
    if (e.name === 'TimeoutError')
        return 'The bank took too long to respond. Please try again. A repeated dispute submission will not create a duplicate case.';
    if (e.status === 0)
        return 'We could not reach the bank. Check your connection and try again.';
    if (e.status === 401)
        return 'Your session has ended. Please sign in again.';
    if (e.status === 403)
        return 'This request could not be authorised. Refresh the page and try again.';
    if (e.status === 409)
        return e.error?.message ?? 'This case changed or already has an active dispute. Refresh and try again.';
    return e.error?.message ?? fallback;
}
