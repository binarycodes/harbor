package io.binarycodes.harbor.library.ui.component;

import java.util.Locale;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

import io.binarycodes.harbor.library.domain.PaletteIndex;

/**
 * The colored tile that stands in for a page's artwork, showing the first two
 * letters of the site over one of the ten cover colors. Saved pages rarely have a
 * usable image, and a letter tile keyed to the site is easier to pick out of a
 * grid than a generic placeholder.
 */
public class CoverTile extends Div {

    private CoverTile(String monogram, int colorIndex) {
        addClassName("cover-tile");
        getStyle().set("background", "var(--color-cover-" + colorIndex + ")");
        Span letters = new Span(monogram);
        letters.addClassName("cover-tile-monogram");
        add(letters);
    }

    public static CoverTile forSite(String site) {
        return new CoverTile(monogram(site), PaletteIndex.forText(site));
    }

    private static String monogram(String site) {
        if (site == null || site.isBlank()) {
            return "?";
        }
        String trimmed = site.strip();
        return trimmed.substring(0, Math.min(2, trimmed.length())).toUpperCase(Locale.ROOT);
    }
}
