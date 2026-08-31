package io.binarycodes.harbor.library.domain;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * What the save-a-link dialog is editing. The URL is typed first; the rest is
 * filled in from the fetched page and then stays editable, so every field can be
 * empty while the dialog is open.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LinkDraft {

    private String url;
    private String title;
    private String description;
    private String site;
    private List<String> tags;
    private BookmarkType type;
    private boolean readLater;
    private int readingMinutes;
    private String content;

    /**
     * The archived PDF, when the page was just read and the deployment renders one
     * before saving. Not bound to any field — the dialog carries it from the fetch to
     * the save without showing it.
     */
    private byte[] archive;

    /**
     * Whether the page behind this draft was read during this visit to the dialog.
     * It is what distinguishes an edit that re-read the page from one that only
     * corrected a title: the first is owed a fresh archive even when no bytes came
     * back with it, and the second must not lose the copy already stored.
     */
    private boolean refetched;

    public List<String> tagsOrEmpty() {
        return tags == null ? List.of() : tags;
    }
}
