package io.binarycodes.harbor.library.service;

import org.springframework.stereotype.Component;

/**
 * Whose library is being read or written. Harbor has no accounts yet, so there is
 * one shared owner and everyone reaching the server gets it.
 *
 * <p>This exists so that adding accounts is a change to this one method rather
 * than to every query: each table carries an owner and each repository call is
 * already scoped by it. When Keycloak lands, {@link #current()} returns the
 * authenticated subject and nothing else has to move.
 */
@Component
public class LibraryOwner {

    static final String SHARED = "public";

    public String current() {
        return SHARED;
    }
}
