export interface SessionIdentity {
    username: string;
    displayName: string;
    role: 'CUSTOMER' | 'STAFF';
}
export interface SessionSignInRequest {
    username: string;
    password: string;
}
