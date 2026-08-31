package io.binarycodes.harbor.library.service;

/**
 * A bookmark whose archive has not arrived, as the database returns it: everything
 * a render needs and nothing else.
 */
interface PendingArchiveRow {

    String getId();

    String getOwnerId();

    String getUrl();

    String getTitle();
}
