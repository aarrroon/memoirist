package com.memoirist.common;

/**
 * The client edited an old copy of an entry: the {@code version} it sent is older than the stored one (D-23). It
 * becomes {@code 409 entry-stale} (API_CONTRACT, Errors).
 *
 * <p>The service throws this when it compares the versions itself. If another request commits between that
 * comparison and this one's commit, Hibernate's own check fails instead, with
 * {@code ObjectOptimisticLockingFailureException}, and the advice answers that one the same way.
 */
public class EntryStaleException extends RuntimeException {

    public EntryStaleException() {
        super("Entry version is older than the stored one.");
    }
}
