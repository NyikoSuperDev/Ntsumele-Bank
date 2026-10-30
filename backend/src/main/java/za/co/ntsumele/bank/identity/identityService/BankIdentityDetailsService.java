package za.co.ntsumele.bank.identity.identityService;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.ntsumele.bank.identity.identityModel.BankIdentity;
import za.co.ntsumele.bank.identity.identityRepository.BankIdentityRepository;

/**
 * class : BankIdentityDetailsService
 * Author : Nyiko Chauke
 * 2026
 *
 * Adapts a stored bank identity into Spring Security user details so the authentication
 * provider can verify its password hash and apply its role.
 */
@Service
@Transactional(readOnly = true)
public class BankIdentityDetailsService implements UserDetailsService {

    private final BankIdentityRepository bankIdentityRepository;

    public BankIdentityDetailsService(BankIdentityRepository bankIdentityRepository) {
        this.bankIdentityRepository = bankIdentityRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        BankIdentity bankIdentity = bankIdentityRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        return User.withUsername(bankIdentity.username())
                .password(bankIdentity.passwordHash())
                .roles(bankIdentity.role())
                .build();
    }
}
