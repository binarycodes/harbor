package io.binarycodes.harbor.library.ui.component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;

/**
 * A tag editor: existing tags as removable chips, with a text box that turns what
 * you type into a chip on Enter or a comma. Backspace in an empty box takes the
 * last chip back, which is what the muscle memory from every other tag field
 * expects.
 *
 * <p>The text box reports every keystroke to the server because Backspace has to
 * know whether the box was already empty when it was pressed.
 */
public class TagChipsField extends CustomField<List<String>> {

    private final Div chips = new Div();
    private final TextField input = new TextField();
    private final List<String> tags = new ArrayList<>();

    public TagChipsField() {
        addClassName("tag-chips-field");

        chips.addClassName("tag-chips-field-chips");

        input.addClassName("tag-chips-field-input");
        input.setPlaceholder(getTranslation("save.tags.placeholder"));
        input.setAriaLabel(getTranslation("save.tags.placeholder"));
        input.setValueChangeMode(ValueChangeMode.EAGER);
        input.addKeyDownListener(Key.ENTER, event -> commitTyped());
        input.addKeyDownListener(Key.COMMA, event -> commitTyped());
        input.addKeyDownListener(Key.BACKSPACE, event -> {
            if (input.getValue().isEmpty()) {
                removeLast();
            }
        });

        Div wrapper = new Div(chips, input);
        wrapper.addClassName("tag-chips-field-wrapper");
        add(wrapper);
    }

    @Override
    protected List<String> generateModelValue() {
        return List.copyOf(tags);
    }

    @Override
    protected void setPresentationValue(List<String> value) {
        tags.clear();
        if (value != null) {
            tags.addAll(new LinkedHashSet<>(value));
        }
        renderChips();
    }

    private void commitTyped() {
        String typed = input.getValue().replace(",", "").strip();
        input.clear();
        if (typed.isEmpty() || tags.contains(typed)) {
            return;
        }
        tags.add(typed);
        renderChips();
        updateValue();
    }

    private void removeLast() {
        if (tags.isEmpty()) {
            return;
        }
        tags.removeLast();
        renderChips();
        updateValue();
    }

    private void remove(String tag) {
        if (tags.remove(tag)) {
            renderChips();
            updateValue();
        }
    }

    private void renderChips() {
        chips.removeAll();
        tags.forEach(tag -> chips.add(chip(tag)));
    }

    private Div chip(String tag) {
        Button remove = new Button(VaadinIcon.CLOSE.create(), event -> remove(tag));
        remove.addThemeVariants(ButtonVariant.TERTIARY);
        remove.addClassName("tag-chips-field-remove");
        String label = getTranslation("save.tags.remove", tag);
        remove.setAriaLabel(label);
        remove.setTooltipText(label);

        Div chip = new Div(new Span(tag), remove);
        chip.addClassName("tag-chips-field-chip");
        return chip;
    }
}
