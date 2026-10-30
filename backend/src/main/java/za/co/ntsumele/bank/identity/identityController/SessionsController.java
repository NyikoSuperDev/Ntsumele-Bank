package za.co.ntsumele.bank.identity.identityController;

import java.security.Principal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.ntsumele.bank.identity.identityDto.SessionCsrfResponse;
import za.co.ntsumele.bank.identity.identityDto.SessionIdentityResponse;
import za.co.ntsumele.bank.identity.identityService.IdentityProfileService;

/**
 * class : SessionsController
 * Author : Nyiko Chauke
 * October/2026
 * Exposes the current CSRF token and the signed-in user's session identity
 * through the session API endpoints.
 */
@RestController
@RequestMapping("/api")
public class SessionsController {

    private final IdentityProfileService identityProfileService;

    public SessionsController(IdentityProfileService identityProfileService) {
        this.identityProfileService = identityProfileService;
    }

    @GetMapping("/csrf")
    public SessionCsrfResponse getCsrfToken(CsrfToken csrfToken) {
        return new SessionCsrfResponse(csrfToken.getToken(), csrfToken.getHeaderName());
    }

    @GetMapping("/me")
    public SessionIdentityResponse getCurrentUser(Principal principal) {
        return identityProfileService.getCurrentUser(principal.getName());
    }
}
