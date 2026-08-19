package io.binarycodes.harbor.security;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;

/**
 * Every route requires an authenticated reader, and the library each one sees is
 * their own.
 *
 * <p>There is no Harbor login view: an unauthenticated request redirects straight to
 * Keycloak. A local form would only collect credentials Harbor has no business
 * seeing, and the identity provider is the thing that knows how to ask.
 *
 * <p>Nothing here grants access on its own. A route without an access annotation is
 * denied by navigation access control, and ownership is enforced a layer down, in
 * SQL, by {@code LibraryOwner}.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * The registration id, which has to agree with
     * {@code spring.security.oauth2.client.registration.keycloak.*} in
     * {@code application.properties} — Spring builds this path from that key.
     */
    private static final String KEYCLOAK_AUTHORIZATION_PATH = "/oauth2/authorization/keycloak";

    /**
     * A year, the shortest max-age browsers accept for HSTS preloading.
     */
    private static final Duration HSTS_MAX_AGE = Duration.ofDays(365);

    /**
     * Harbor asks the browser for none of these. Denying them outright means a
     * script that somehow reaches the page cannot reach the camera or the location
     * either.
     */
    private static final String DENIED_FEATURES = "accelerometer=(), camera=(), display-capture=(),"
            + " geolocation=(), gyroscope=(), magnetometer=(), microphone=(), midi=(), payment=(),"
            + " serial=(), usb=()";

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
            LogoutSuccessHandler oidcLogoutSuccessHandler) throws Exception {
        return http.with(VaadinSecurityConfigurer.vaadin(), vaadin -> vaadin
                        .oauth2LoginPage(KEYCLOAK_AUTHORIZATION_PATH)
                        .logoutSuccessHandler(oidcLogoutSuccessHandler))
                .headers(headers -> {
                    headers.referrerPolicy(referrer -> referrer
                            .policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                    headers.crossOriginOpenerPolicy(opener -> opener
                            .policy(CrossOriginOpenerPolicy.SAME_ORIGIN));
                    headers.permissionsPolicyHeader(permissions -> permissions.policy(DENIED_FEATURES));
                    headers.httpStrictTransportSecurity(hsts -> hsts
                            .includeSubDomains(true)
                            .maxAgeInSeconds(HSTS_MAX_AGE.toSeconds()));
                    // Spring Security normally writes its headers as the response
                    // commits, which never happens for the response Vaadin renders the
                    // page into — static resources got the headers and the application's
                    // own routes got none. Writing them eagerly covers both.
                    headers.addObjectPostProcessor(new ObjectPostProcessor<HeaderWriterFilter>() {

                        @Override
                        public <O extends HeaderWriterFilter> O postProcess(O filter) {
                            filter.setShouldWriteHeadersEagerly(true);
                            return filter;
                        }
                    });
                })
                .build();
    }

    /**
     * Signing out of Harbor has to sign the reader out of Keycloak too. Dropping only
     * the local session leaves the identity provider's intact, so the redirect back
     * comes straight through it and signs in again — which reads as a dead button
     * rather than a session that outlived the click.
     */
    @Bean
    public LogoutSuccessHandler oidcLogoutSuccessHandler(ClientRegistrationRepository clientRegistrations) {
        OidcClientInitiatedLogoutSuccessHandler handler =
                new OidcClientInitiatedLogoutSuccessHandler(clientRegistrations);
        handler.setPostLogoutRedirectUri("{baseUrl}");
        return handler;
    }
}
