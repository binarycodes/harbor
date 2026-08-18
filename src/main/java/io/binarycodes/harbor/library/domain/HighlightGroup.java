package io.binarycodes.harbor.library.domain;

import java.util.List;

/**
 * Everything the reader kept from one page, and just enough about the page to say
 * where it came from. The highlights screen shows the passages themselves, so
 * these it does need — the article body it still does not.
 */
public record HighlightGroup(String id, String title, String site, List<Highlight> highlights) {

    public HighlightGroup {
        highlights = highlights == null ? List.of() : List.copyOf(highlights);
    }
}
