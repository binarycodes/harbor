package io.binarycodes.harbor.base.ui;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * The panel shown where a listing would be when there is nothing to list: an
 * icon, a short statement of what is missing, and a hint about how to fix it.
 */
public class EmptyState extends VerticalLayout {

    private final Div iconFrame = new Div();
    private final Span title = new Span();
    private final Span hint = new Span();

    public EmptyState() {
        addClassName("empty-state");
        setSpacing(false);
        setPadding(false);
        setAlignItems(Alignment.CENTER);
        iconFrame.addClassName("empty-state-icon");
        title.addClassName("empty-state-title");
        hint.addClassName("empty-state-hint");
        add(iconFrame, title, hint);
    }

    public void update(Icon icon, String titleText, String hintText) {
        iconFrame.removeAll();
        iconFrame.add(icon);
        title.setText(titleText);
        hint.setText(hintText);
    }
}
