package io.binarycodes.harbor;

import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import io.binarycodes.harbor.library.service.LibraryOwner;

/**
 * Stands in for Keycloak. The redirect, the token exchange and the subject
 * extraction are exercised for real exactly once, in {@code HarborJourneyIT}, which
 * is the only tier with a browser to drive a login form with; every other tier says
 * who it is and gets on with what it is actually testing.
 *
 * <p>The owner is switchable rather than fixed, which is what lets a test act as two
 * readers — the only way owner isolation can be asserted at all.
 */
@TestConfiguration
public class StubIdentityConfiguration {

    public static final String READER = "reader-11111111-1111-1111-1111-111111111111";
    public static final String OTHER_READER = "reader-22222222-2222-2222-2222-222222222222";

    private static final String CLIENT_REGISTRATION_ID = "keycloak";
    private static final String AUTHORITY = "OIDC_USER";

    /**
     * Whose library the service layer sees. Package-visible state rather than a
     * constructor argument because the bean is built once per context and a test
     * changes its mind partway through.
     */
    private String subject = READER;

    @Bean
    @Primary
    public LibraryOwner switchableLibraryOwner() {
        return () -> subject;
    }

    /**
     * Act as this reader from now on. Whoever is set here is the owner of every row
     * written afterwards, so a test switches and then asserts the other library is
     * empty.
     */
    public void actAs(String readerSubject) {
        subject = readerSubject;
    }

    /**
     * Puts an authenticated reader in the security context. Browserless tests bypass
     * the servlet filter chain, so nothing has authenticated them — but they still
     * navigate through Vaadin's {@code SpringNavigationAccessControl}, which denies a
     * route to a reader it cannot see.
     */
    public static void authenticate(String readerSubject) {
        SecurityContextHolder.getContext().setAuthentication(tokenFor(readerSubject));
    }

    /**
     * An authentication that is not OIDC at all, for asserting what
     * {@code AuthenticatedLibraryOwner} does with one.
     */
    public static void authenticateWithoutOidc() {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken("someone", "credentials", AUTHORITY));
    }

    public static void forget() {
        SecurityContextHolder.clearContext();
    }

    private static OAuth2AuthenticationToken tokenFor(String readerSubject) {
        return new OAuth2AuthenticationToken(oidcUserFor(readerSubject),
                List.of(new SimpleGrantedAuthority(AUTHORITY)), CLIENT_REGISTRATION_ID);
    }

    /**
     * The claims Harbor reads and nothing more: the subject it owns rows by, and the
     * username the drawer shows. The id token carries no signature because nothing
     * here verifies one — that happens in Keycloak's own tier.
     */
    private static OidcUser oidcUserFor(String readerSubject) {
        Map<String, Object> claims = Map.of(
                StandardClaimNames.SUB, readerSubject,
                StandardClaimNames.PREFERRED_USERNAME, readerSubject);
        return new DefaultOidcUser(List.of(new SimpleGrantedAuthority(AUTHORITY)),
                new OidcIdToken("id-token", null, null, claims),
                StandardClaimNames.PREFERRED_USERNAME);
    }
}
