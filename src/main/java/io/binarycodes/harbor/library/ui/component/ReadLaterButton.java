package io.binarycodes.harbor.library.ui.component;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;

import io.binarycodes.harbor.library.domain.BookmarkSummary;

/**
 * The queue-for-later toggle that sits on every rendering of a bookmark. It stops
 * the click from reaching the surrounding card, which would otherwise open the
 * reader at the same time.
 */
public class ReadLaterButton extends Button {

    public ReadLaterButton(BookmarkSummary bookmark, BookmarkActions actions) {
        super(bookmark.readLater() ? VaadinIcon.BOOKMARK.create() : VaadinIcon.BOOKMARK_O.create());
        addThemeVariants(ButtonVariant.TERTIARY);
        addClassName("read-later-button");
        setClassName("queued", bookmark.readLater());

        String label = getTranslation(bookmark.readLater()
                ? "bookmark.read_later.remove"
                : "bookmark.read_later.add");
        setAriaLabel(label);
        setTooltipText(label);

        addClickListener(event -> actions.toggleReadLater(bookmark));
        getElement().addEventListener("click", event -> {
        }).stopPropagation();
    }
}
