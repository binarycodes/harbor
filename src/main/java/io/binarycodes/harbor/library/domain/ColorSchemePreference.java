package io.binarycodes.harbor.library.domain;

/**
 * The visitor's light/dark choice. {@link #SYSTEM} means no choice has been made
 * yet and the operating system decides.
 */
public enum ColorSchemePreference {

    SYSTEM,
    LIGHT,
    DARK
}
