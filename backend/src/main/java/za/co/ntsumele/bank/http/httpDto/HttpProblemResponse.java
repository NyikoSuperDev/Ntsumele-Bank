package za.co.ntsumele.bank.http.httpDto;

/**
 * class : HttpProblemResponse
 * Author : Nyiko Chauke
 * 2026
 *
 * Defines the JSON error body returned to clients, containing an application error code and a readable message.
 */
public record HttpProblemResponse(String code, String message) {
}
