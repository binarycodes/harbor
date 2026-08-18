package io.binarycodes.harbor.library.ui.component;

import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;

import io.binarycodes.harbor.library.ui.presenter.LibraryFilter;

/**
 * Repeats the sidebar's tag selection above the listing, so the reason a listing
 * looks short is visible without glancing away, and each tag can be dropped from
 * where the effect is.
 */
public class ActiveTagChips extends Div {

    private final LibraryFilter libraryFilter;

    public ActiveTagChips(LibraryFilter libraryFilter) {
        this.libraryFilter = libraryFilter;
        addClassName("active-tag-chips");
    }

    public void refresh() {
        removeAll();
        setVisible(libraryFilter.hasSelectedTags());
        if (!libraryFilter.hasSelectedTags()) {
            return;
        }
        Span label = new Span(getTranslation("library.filtered_by"));
        label.addClassName("active-tag-chips-label");
        add(label);
        libraryFilter.getSelectedTags().forEach(tag -> add(chip(tag)));
    }

    private Badge chip(String tag) {
        Button remove = new Button(VaadinIcon.CLOSE.create(), event -> libraryFilter.toggleTag(tag));
        remove.addThemeVariants(ButtonVariant.TERTIARY);
        remove.addClassName("active-tag-chip-remove");
        String label = getTranslation("library.filter.remove", tag);
        remove.setAriaLabel(label);
        remove.setTooltipText(label);

        Badge chip = new Badge(tag);
        chip.addClassName("active-tag-chip");
        chip.getElement().appendChild(remove.getElement());
        return chip;
    }
}
