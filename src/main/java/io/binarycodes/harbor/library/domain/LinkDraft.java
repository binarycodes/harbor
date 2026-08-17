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

    public List<String> tagsOrEmpty() {
        return tags == null ? List.of() : tags;
    }
}
