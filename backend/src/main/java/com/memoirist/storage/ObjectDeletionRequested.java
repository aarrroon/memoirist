package com.memoirist.storage;

import java.util.List;

/**
 * "Delete these stored objects once the current transaction has committed." Published by
 * {@link StorageService#deleteAfterCommit} and handled by {@link ObjectDeletionListener}.
 *
 * <p>It carries object keys only (non-negotiable 5: the database and the app never hold URLs).
 *
 * @param objectKeys the keys to delete; copied, so a caller changing its own list afterwards changes nothing
 */
public record ObjectDeletionRequested(List<String> objectKeys) {

    public ObjectDeletionRequested {
        // List.copyOf makes the list unmodifiable and throws NullPointerException on a null key.
        objectKeys = List.copyOf(objectKeys);
    }
}
