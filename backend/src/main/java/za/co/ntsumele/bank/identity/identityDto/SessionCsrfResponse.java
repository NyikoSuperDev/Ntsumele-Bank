package za.co.ntsumele.bank.identity.identityDto;

/**
 * class : SessionCsrfResponse
 * Author : Nyiko Chauke
 * 2026
 *
 * Returns the session's CSRF token and the HTTP header name clients must use
 * when sending protected mutations.
 */
public record SessionCsrfResponse(String token, String headerName) {
}
