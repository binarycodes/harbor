package io.binarycodes.harbor.security;

import java.time.Duration;

import org.springframework.boot.security.autoconfigure.web.servlet.PathRequest;
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
 * the identity provider. A local form would only collect credentials Harbor has no
 * business seeing, and the provider is the thing that knows how to ask.
 *
 * <p>Which provider is a deployment's choice and appears nowhere in this class. Harbor
 * is an OpenID Connect client and needs no more than that.
 *
 * <p>Nothing here grants access to a route on its own. A route without an access
 * annotation is denied by navigation access control, and ownership is enforced a layer
 * down, in SQL, by {@code LibraryOwner}. The one thing this class does open up is the
 * static resource directories, which hold stylesheets and nothing of any reader's.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Where an unauthenticated request is sent. The last segment is the registration id,
     * so this has to agree with
     * {@code spring.security.oauth2.client.registration.oidc.*} in
     * {@code application.properties} — Spring builds both this path and the
     * {@code /login/oauth2/code/oidc} callback from that one key.
     *
     * <p>{@code oidc} names the protocol rather than a product, because a deployment's
     * choice of provider should not end up in Harbor's URLs.
     */
    private static final String AUTHORIZATION_PATH = "/oauth2/authorization/oidc";

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
        // Vaadin publishes styles.css itself, but the @imports inside it are requests
        // of their own and inherit nothing from it, so every partial came back 403 and
        // the application rendered unstyled. Spring Boot's common static locations are
        // what Vaadin's own styling documentation reaches for here, and they cover
        // css/ — where these partials already live — along with the other directories
        // a static file would be put in, so the next one added needs no rule of its
        // own. Registered before Vaadin's rules, because the anyRequest they end with
        // would otherwise match first.
        return http.authorizeHttpRequests(requests -> requests
                        .requestMatchers(PathRequest.toStaticResources().atCommonLocations())
                        .permitAll())
                .with(VaadinSecurityConfigurer.vaadin(), vaadin -> vaadin
                        .oauth2LoginPage(AUTHORIZATION_PATH)
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
     * Signing out of Harbor has to sign the reader out of the identity provider too.
     * Dropping only the local session leaves the provider's intact, so the redirect back
     * comes straight through it and signs in again — which reads as a dead button rather
     * than a session that outlived the click.
     *
     * <p>RP-initiated logout is part of OpenID Connect, so this asks nothing of a
     * provider that a Harbor deployment could not expect from any of them.
     */
    @Bean
    public LogoutSuccessHandler oidcLogoutSuccessHandler(ClientRegistrationRepository clientRegistrations) {
        OidcClientInitiatedLogoutSuccessHandler handler =
                new OidcClientInitiatedLogoutSuccessHandler(clientRegistrations);
        handler.setPostLogoutRedirectUri("{baseUrl}");
        return handler;
    }
}
