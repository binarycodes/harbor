package io.binarycodes.harbor.library.ui.component;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.icon.VaadinIcon;

/**
 * The button that appears over a text selection in the reader, offering to keep
 * the passage. It is positioned in viewport coordinates because that is what the
 * browser reports for a selection.
 */
public class SelectionHighlightButton extends Button {

    public SelectionHighlightButton(Runnable onHighlight) {
        super(VaadinIcon.PENCIL.create());
        setText(getTranslation("reader.highlight"));
        addClassName("selection-highlight-button");
        setVisible(false);
        addClickListener(event -> onHighlight.run());
    }

    public void showAt(double centerX, double top) {
        getStyle().set("left", centerX + "px").set("top", top + "px");
        setVisible(true);
    }

    public void hide() {
        setVisible(false);
    }
}
