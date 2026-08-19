package io.binarycodes.harbor.library.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

/**
 * The library belongs to whoever the identity provider says is reading it. The subject
 * claim rather than the username or the email: those are things a provider's
 * administrator can change, and a library that follows a renamed account is not a
 * library.
 *
 * <p>Vaadin's own threads reach the context because
 * {@code VaadinAwareSecurityContextHolderStrategy} resolves it from the Vaadin
 * session, so nothing here has to be handed an authentication.
 */
@Component
class AuthenticatedLibraryOwner implements LibraryOwner {

    /**
     * No fallback owner. Anything reaching the library without an authenticated
     * reader is a bug, and the shape it used to take — writing into one shared
     * library — is the bug accounts exist to remove. Failing here makes it visible
     * instead.
     */
    @Override
    public String current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof OAuth2AuthenticationToken token
                && token.getPrincipal() instanceof OidcUser reader) {
            return reader.getSubject();
        }
        throw new IllegalStateException("No authenticated reader; refusing to guess whose library this is");
    }
}
