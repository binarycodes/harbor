package io.binarycodes.harbor.library.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.binarycodes.harbor.StubIdentityConfiguration;

/**
 * Who the library belongs to, and what happens when nobody can say. The refusal is
 * the interesting half: a fallback owner here would put an unauthenticated code path
 * back to writing into one shared library, which is the whole reason accounts exist.
 */
@DisplayName("The library's owner")
class AuthenticatedLibraryOwnerTest {

    private final LibraryOwner owner = new AuthenticatedLibraryOwner();

    @AfterEach
    void forgetTheReader() {
        StubIdentityConfiguration.forget();
    }

    @Test
    @DisplayName("is the subject of the signed-in reader")
    void isTheAuthenticatedSubject() {
        StubIdentityConfiguration.authenticate(StubIdentityConfiguration.READER);

        assertEquals(StubIdentityConfiguration.READER, owner.current());
    }

    @Test
    @DisplayName("refuses to guess when nobody is signed in")
    void refusesAnEmptyContext() {
        assertThrows(IllegalStateException.class, owner::current);
    }

    @Test
    @DisplayName("refuses an authentication that carries no OIDC reader")
    void refusesANonOidcAuthentication() {
        StubIdentityConfiguration.authenticateWithoutOidc();

        assertThrows(IllegalStateException.class, owner::current);
    }
}
