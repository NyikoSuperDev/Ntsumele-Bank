package za.co.ntsumele.bank.identity.identityConfig;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import za.co.ntsumele.bank.audit.auditService.AuditTrailService;
import za.co.ntsumele.bank.http.httpFilter.JsonRequestSizeFilter;
import za.co.ntsumele.bank.http.httpWriter.HttpFailureWriter;
import za.co.ntsumele.bank.identity.identityFilter.SignInAttemptLimitFilter;
import za.co.ntsumele.bank.identity.identityFilter.HttpsEnforcementFilter;

/**
 * class : SessionSecurityConfig
 * Author : Nyiko Chauke
 * 2026
 *
 * Configures session authentication, customer/staff endpoint permissions, CSRF protection,
 * password hashing and security headers. It installs request-size/login-attempt limits
 * and records sign-in and sign-out audit events.
 */
@Configuration
public class SessionSecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            UserDetailsService userDetailsService,
            AuditTrailService auditTrailService,
            HttpFailureWriter httpFailureWriter,
            Clock clock,
            @Value("${app.security.require-https:false}") boolean requireHttps,
            @Value("${app.security.public-origin:}") String publicOrigin) throws Exception {
        if (requireHttps) {
            http.addFilterBefore(new HttpsEnforcementFilter(publicOrigin, httpFailureWriter), CsrfFilter.class);
        }
        http.userDetailsService(userDetailsService)
                .csrf(csrf -> csrf.csrfTokenRepository(new HttpSessionCsrfTokenRepository()))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/csrf", "/api/login", "/actuator/health", "/error").permitAll()
                        .requestMatchers("/api/staff/**").hasRole("STAFF")
                        .requestMatchers("/api/me", "/api/logout").authenticated()
                        .requestMatchers("/api/**").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/*.js", "/*.css", "/*.ico",
                                "/*.svg", "/assets/**").permitAll()
                        .anyRequest().denyAll())
                .formLogin(form -> form.loginProcessingUrl("/api/login")
                        .successHandler((request, response, authentication) -> {
                            auditTrailService.recordAction(authentication.getName(), "LOGIN_SUCCESS", null, "LOGIN_SUCCESS");
                            response.setStatus(200);
                        })
                        .failureHandler((request, response, exception) -> {
                            // Do not place an untrusted submitted username in the audit log.
                            auditTrailService.recordAction("anonymous", "LOGIN_FAILURE", null, "LOGIN_FAILURE");
                            httpFailureWriter.writeError(
                                    response, 401, "UNAUTHORIZED", "Invalid username or password.");
                        }))
                .logout(logout -> logout.logoutUrl("/api/logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .addLogoutHandler((request, response, authentication) -> {
                            if (authentication != null) {
                                auditTrailService.recordAction(authentication.getName(), "LOGOUT", null, "LOGOUT");
                            }
                        })
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> httpFailureWriter.writeError(
                                response, 401, "UNAUTHORIZED", "Sign in to continue."))
                        .accessDeniedHandler((request, response, exception) -> httpFailureWriter.writeError(
                                response, 403, "FORBIDDEN",
                                "The request is not allowed or its security token has expired.")))
                .sessionManagement(sessions -> sessions.sessionFixation(fixation -> fixation.migrateSession()))
                .headers(headers -> headers
                        .httpStrictTransportSecurity(hsts -> hsts.maxAgeInSeconds(31_536_000)
                                .includeSubDomains(true).preload(false))
                        .contentSecurityPolicy(policy -> policy.policyDirectives(
                                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                                        + "img-src 'self' data:; connect-src 'self'; font-src 'self'; "
                                        + "frame-ancestors 'none'; base-uri 'self'; form-action 'self'"))
                        .referrerPolicy(policy -> policy.policy(ReferrerPolicy.NO_REFERRER))
                        .permissionsPolicyHeader(policy -> policy.policy(
                                "camera=(), microphone=(), geolocation=()")))
                // Construct filters here so Boot does not also register them as servlet filters.
                .addFilterBefore(new SignInAttemptLimitFilter(clock, httpFailureWriter),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new JsonRequestSizeFilter(httpFailureWriter), CsrfFilter.class);
        return http.build();
    }
}
