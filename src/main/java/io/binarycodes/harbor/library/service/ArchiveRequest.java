package io.binarycodes.harbor.library.service;

/**
 * One page waiting to be archived. Everything a render needs, and enough to file the
 * result: no session, no entity, and nothing that stops being true while it waits.
 *
 * <p>The owner is carried rather than looked up. A render finishes on a thread with
 * no reader signed in to it, and {@link LibraryOwner} is right to refuse there rather
 * than guess — so whose library this belongs to is settled when the work is queued,
 * by the reader who asked for it.
 */
record ArchiveRequest(String bookmarkId, String ownerId, String url, String title) {
}
