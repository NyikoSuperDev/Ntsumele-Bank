package za.co.ntsumele.bank.identity.identityService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.ntsumele.bank.failure.failureException.BankingRuleViolation;
import za.co.ntsumele.bank.identity.identityDto.SessionIdentityResponse;
import za.co.ntsumele.bank.identity.identityModel.BankIdentity;
import za.co.ntsumele.bank.identity.identityRepository.BankIdentityRepository;

/**
 * class : IdentityProfileService
 * Author : Nyiko Chauke
 * 2026
 *
 * Loads the current username's identity and returns only the client-facing username,
 * display name and role.
 */
@Service
@Transactional(readOnly = true)
public class IdentityProfileService {
    private final BankIdentityRepository bankIdentityRepository;

    public IdentityProfileService(BankIdentityRepository bankIdentityRepository) {
        this.bankIdentityRepository = bankIdentityRepository;
    }

    public SessionIdentityResponse getCurrentUser(String username) {
        BankIdentity user = bankIdentityRepository.findByUsername(username).orElseThrow(BankingRuleViolation::notFound);
        return new SessionIdentityResponse(user.username(), user.displayName(), user.role());
    }
}
