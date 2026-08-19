package io.binarycodes.harbor.library.service;

/**
 * Whose library is being read or written. Every table carries an owner and every
 * query is scoped by one, so this is the only place that decides who that is.
 *
 * <p>An interface with one implementation because a test needs to act as two
 * different readers, which is the only way owner isolation can be asserted at all.
 */
public interface LibraryOwner {

    String current();
}
