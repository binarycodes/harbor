package io.binarycodes.harbor.library.ui.component;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;

/**
 * The delete control that sits on every rendering of a bookmark. Like the
 * queue-for-later toggle it stops the click from reaching the surrounding card,
 * which would otherwise open the reader on the way to deleting it.
 *
 * <p>It takes what to do rather than the bookmark, so the reader can offer the same
 * control without inventing a {@link BookmarkActions} for a single article.
 */
public class DeleteBookmarkButton extends Button {

    public DeleteBookmarkButton(Runnable onDelete) {
        super(VaadinIcon.TRASH.create());
        addThemeVariants(ButtonVariant.TERTIARY);
        addClassName("delete-bookmark-button");

        String label = getTranslation("bookmark.delete");
        setAriaLabel(label);
        setTooltipText(label);

        addClickListener(event -> onDelete.run());
        getElement().addEventListener("click", event -> {
        }).stopPropagation();
    }

    /**
     * Spells the action out beside the icon, for the reader, where every action in the
     * bar carries its own words. The tooltip goes with it rather than repeating what is
     * already on screen.
     */
    public void showLabel() {
        setText(getTranslation("bookmark.delete"));
        setTooltipText(null);
    }
}
