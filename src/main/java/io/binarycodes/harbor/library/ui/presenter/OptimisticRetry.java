package io.binarycodes.harbor.library.ui.presenter;

import org.springframework.dao.OptimisticLockingFailureException;

/**
 * Doing something once, and once more if another session got there first.
 *
 * <p>Only safe for a change expressed in terms of what the bookmark currently
 * says — flip the flag, append the passage — because the second attempt reads
 * again and recomputes. An overwrite must not come through here: retrying one
 * would discard whatever the other session wrote.
 *
 * <p>The retry cannot live inside the transaction that lost the race, which is
 * already marked for rollback by the time the conflict surfaces. Each attempt is
 * a fresh call, and so a fresh transaction.
 */
final class OptimisticRetry {

    private OptimisticRetry() {
    }

    /**
     * @return whether the change was applied; false means it lost twice, which is a
     *         genuinely contended bookmark rather than a near miss
     */
    static boolean once(Runnable mutation) {
        try {
            mutation.run();
            return true;
        } catch (OptimisticLockingFailureException changedElsewhere) {
            try {
                mutation.run();
                return true;
            } catch (OptimisticLockingFailureException stillContended) {
                return false;
            }
        }
    }
}
