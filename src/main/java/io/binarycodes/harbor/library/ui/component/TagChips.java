package io.binarycodes.harbor.library.ui.component;

import java.util.List;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

/**
 * A bookmark's tags, shown but not clickable — filtering happens from the
 * sidebar, where the whole vocabulary is visible at once.
 */
public class TagChips extends Div {

    public TagChips(List<String> tags) {
        addClassName("tag-chips");
        tags.forEach(tag -> {
            Span chip = new Span(tag);
            chip.addClassName("tag-chip");
            add(chip);
        });
        setVisible(!tags.isEmpty());
    }
}
