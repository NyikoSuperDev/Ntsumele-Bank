package za.co.ntsumele.bank.identity.identityModel;

/**
 * class : BankIdentity
 * Author : Nyiko Chauke
 * 2026
 *
 * Represents a stored user identity, including its password hash and customer or staff role,
 * for authentication and profile lookup.
 */
public record BankIdentity(
        String username,
        String displayName,
        String passwordHash,
        String role) {
}
