package io.binarycodes.harbor.library.ui.component;

import java.util.List;
import java.util.function.IntConsumer;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;

import io.binarycodes.harbor.library.domain.Highlight;

/**
 * The passages kept from the article currently open, each removable.
 */
public class HighlightList extends Div {

    private final IntConsumer onRemove;

    public HighlightList(IntConsumer onRemove) {
        this.onRemove = onRemove;
        addClassName("highlight-list");
    }

    public void setHighlights(List<Highlight> highlights) {
        removeAll();
        if (highlights.isEmpty()) {
            add(placeholder());
            return;
        }
        for (int index = 0; index < highlights.size(); index++) {
            add(passage(highlights.get(index), index));
        }
    }

    private Div passage(Highlight highlight, int index) {
        Button remove = new Button(VaadinIcon.CLOSE.create(), event -> onRemove.accept(index));
        remove.addThemeVariants(ButtonVariant.TERTIARY);
        remove.addClassName("highlight-remove");
        remove.setAriaLabel(getTranslation("reader.highlight.remove"));
        remove.setTooltipText(getTranslation("reader.highlight.remove"));

        Paragraph text = new Paragraph(highlight.text());
        text.addClassName("highlight-passage");

        Div passage = new Div(text, remove);
        passage.addClassName("highlight-list-item");
        return passage;
    }

    private Div placeholder() {
        Span hint = new Span(getTranslation("reader.highlight.hint"));
        Div placeholder = new Div(VaadinIcon.QUOTE_RIGHT.create(), hint);
        placeholder.addClassName("highlight-list-placeholder");
        return placeholder;
    }
}
