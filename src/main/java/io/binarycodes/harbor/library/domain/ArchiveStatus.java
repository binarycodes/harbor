package io.binarycodes.harbor.library.domain;

/**
 * Where a bookmark's archived copy of its page has got to.
 *
 * <p>Only meaningful because archiving can outlive the save. When
 * {@code harbor.archive.force-before-save} is on, a bookmark is only ever
 * {@link #READY} — the save does not return until the render has.
 */
public enum ArchiveStatus {

    /**
     * The render is queued or running. There is no archive to offer yet, and the
     * reader is told so rather than shown nothing.
     */
    PENDING,

    READY,

    /**
     * No archive, and none coming: the render was attempted and did not produce one,
     * or the bookmark came from an import that never had a page to render.
     */
    FAILED
}
