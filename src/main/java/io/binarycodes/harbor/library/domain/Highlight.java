package io.binarycodes.harbor.library.domain;

/**
 * A passage the reader selected in an article and chose to keep.
 */
public record Highlight(String text) {

    public Highlight {
        text = text == null ? "" : text.strip();
    }
}
