package za.co.ntsumele.bank.identity.identityDto;

/**
 * class : SessionIdentityResponse
 * Author : Nyiko Chauke
 * 2026
 *
 * Contains the signed-in user's username, display name and application role for
 * the client workspace, without exposing the stored password hash.
 */
public record SessionIdentityResponse(String username, String displayName, String role) {
}
