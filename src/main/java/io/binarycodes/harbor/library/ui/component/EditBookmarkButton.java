package io.binarycodes.harbor.library.ui.component;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;

/**
 * The edit control that sits beside the delete one on every rendering of a bookmark.
 * Like its neighbours it stops the click from reaching the surrounding card, which
 * would otherwise open the reader behind the dialog.
 */
public class EditBookmarkButton extends Button {

    public EditBookmarkButton(Runnable onEdit) {
        super(VaadinIcon.PENCIL.create());
        addThemeVariants(ButtonVariant.TERTIARY);
        addClassName("edit-bookmark-button");

        String label = getTranslation("bookmark.edit");
        setAriaLabel(label);
        setTooltipText(label);

        addClickListener(event -> onEdit.run());
        getElement().addEventListener("click", event -> {
        }).stopPropagation();
    }

    /**
     * Spells the action out beside the icon, for the reader, where every action in the
     * bar carries its own words.
     */
    public void showLabel() {
        setText(getTranslation("bookmark.edit"));
        setTooltipText(null);
    }
}
