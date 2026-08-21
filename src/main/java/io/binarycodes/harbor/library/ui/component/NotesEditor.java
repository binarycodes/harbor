package io.binarycodes.harbor.library.ui.component;

import java.util.function.Consumer;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.markdown.Markdown;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.value.ValueChangeMode;

/**
 * The reader's own notes on an article, written in Markdown with a preview beside
 * it. Notes save as they are typed, so there is no button to forget to press.
 */
public class NotesEditor extends VerticalLayout {

    private final TextArea editor = new TextArea();
    private final Markdown preview = new Markdown();
    private final Button writeButton;
    private final Button previewButton;

    public NotesEditor(Consumer<String> onNotesChanged) {
        addClassName("notes-editor");
        setSizeFull();
        setPadding(false);
        setSpacing(false);

        editor.addClassName("notes-editor-input");
        editor.setPlaceholder(getTranslation("reader.notes.placeholder"));
        editor.setAriaLabel(getTranslation("reader.notes.label"));
        editor.setSizeFull();
        editor.setValueChangeMode(ValueChangeMode.LAZY);
        editor.addValueChangeListener(event -> onNotesChanged.accept(event.getValue()));

        preview.getElement().getClassList().add("notes-editor-preview");
        preview.setSizeFull();

        writeButton = modeButton("reader.notes.write", () -> showPreview(false));
        previewButton = modeButton("reader.notes.preview", () -> showPreview(true));

        add(modeBar(), editor, preview);
        setFlexGrow(1, editor);
        setFlexGrow(1, preview);
        showPreview(false);
    }

    public void setNotes(String notes) {
        String value = notes == null ? "" : notes;
        if (!editor.getValue().equals(value)) {
            editor.setValue(value);
        }
        preview.setContent(value.isBlank() ? getTranslation("reader.notes.empty") : value);
    }

    private HorizontalLayout modeBar() {
        Div modes = new Div(writeButton, previewButton);
        modes.addClassName("notes-editor-modes");
        modes.getElement().setAttribute("role", "group");

        Span format = new Span(VaadinIcon.TEXT_LABEL.create(), new Span(getTranslation("reader.notes.markdown")));
        format.addClassName("notes-editor-format");

        HorizontalLayout bar = new HorizontalLayout(modes, format);
        bar.addClassName("notes-editor-bar");
        bar.setWidthFull();
        bar.setPadding(false);
        bar.setAlignItems(Alignment.CENTER);
        bar.setJustifyContentMode(JustifyContentMode.BETWEEN);
        return bar;
    }

    private Button modeButton(String labelKey, Runnable action) {
        Button button = new Button(getTranslation(labelKey), event -> action.run());
        button.addThemeVariants(ButtonVariant.TERTIARY);
        button.addClassName("notes-editor-mode");
        return button;
    }

    private void showPreview(boolean previewing) {
        editor.setVisible(!previewing);
        preview.setVisible(previewing);
        writeButton.setClassName("selected", !previewing);
        previewButton.setClassName("selected", previewing);
        writeButton.getElement().setAttribute("aria-pressed", String.valueOf(!previewing));
        previewButton.getElement().setAttribute("aria-pressed", String.valueOf(previewing));
    }
}
