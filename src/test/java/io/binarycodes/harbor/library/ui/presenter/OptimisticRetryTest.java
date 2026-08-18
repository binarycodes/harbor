package io.binarycodes.harbor.library.ui.presenter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

@DisplayName("Retrying a change another session got to first")
class OptimisticRetryTest {

    @Test
    @DisplayName("does not retry what worked")
    void appliesOnFirstAttempt() {
        AtomicInteger attempts = new AtomicInteger();

        assertTrue(OptimisticRetry.once(attempts::incrementAndGet));

        assertEquals(1, attempts.get());
    }

    @Test
    @DisplayName("reads again and reapplies after losing once")
    void retriesAfterOneConflict() {
        AtomicInteger attempts = new AtomicInteger();

        boolean applied = OptimisticRetry.once(() -> {
            if (attempts.incrementAndGet() == 1) {
                throw new OptimisticLockingFailureException("someone else was first");
            }
        });

        assertTrue(applied);
        assertEquals(2, attempts.get());
    }

    /**
     * Twice is a bookmark two people are actually fighting over, not a near miss, so
     * it stops rather than looping — and reports that it stopped.
     */
    @Test
    @DisplayName("gives up after losing twice, and says so")
    void givesUpAfterTwoConflicts() {
        AtomicInteger attempts = new AtomicInteger();

        boolean applied = OptimisticRetry.once(() -> {
            attempts.incrementAndGet();
            throw new OptimisticLockingFailureException("still contended");
        });

        assertFalse(applied);
        assertEquals(2, attempts.get());
    }

    /**
     * Anything that is not a lost race is a real failure and has no business being
     * swallowed or repeated.
     */
    @Test
    @DisplayName("lets any other failure straight through, unretried")
    void doesNotRetryOtherFailures() {
        AtomicInteger attempts = new AtomicInteger();

        try {
            OptimisticRetry.once(() -> {
                attempts.incrementAndGet();
                throw new IllegalStateException("something else entirely");
            });
        } catch (IllegalStateException expected) {
            assertEquals(1, attempts.get());
            return;
        }
        throw new AssertionError("The failure should have propagated");
    }
}
