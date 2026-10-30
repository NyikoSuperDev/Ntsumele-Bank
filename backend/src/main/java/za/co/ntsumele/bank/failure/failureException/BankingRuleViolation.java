package za.co.ntsumele.bank.failure.failureException;

/**
 * class : BankingRuleViolation
 * Author : Nyiko Chauke
 * 2026
 *
 * Carries a business-rule failure category, application error code and message for missing resources, conflicts or invalid requests.
 * Keeps these application failures independent of the HTTP response layer.
 */
public class BankingRuleViolation extends RuntimeException {

    public enum Category {
        NOT_FOUND,
        CONFLICT,
        BAD_REQUEST
    }

    private final Category category;
    private final String code;

    private BankingRuleViolation(Category category, String code, String message) {
        super(message);
        this.category = category;
        this.code = code;
    }

    public Category getCategory() {
        return category;
    }

    public String getCode() {
        return code;
    }

    public static BankingRuleViolation notFound() {
        return new BankingRuleViolation(Category.NOT_FOUND, "NOT_FOUND", "Resource not found.");
    }

    public static BankingRuleViolation conflict(String code, String message) {
        return new BankingRuleViolation(Category.CONFLICT, code, message);
    }

    public static BankingRuleViolation badRequest(String code, String message) {
        return new BankingRuleViolation(Category.BAD_REQUEST, code, message);
    }
}
