package io.binarycodes.harbor.library.ui.component;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.TabSheet;

import io.binarycodes.harbor.library.domain.Bookmark;

/**
 * The panel beside the article, holding the reader's notes and the passages they
 * kept.
 */
public class ReaderSidePanel extends TabSheet {

    private final NotesEditor notesEditor;
    private final HighlightList highlightList;
    private final Span highlightsLabel = new Span();
    private final Tab highlightsTab;

    public ReaderSidePanel(Consumer<String> onNotesChanged, IntConsumer onRemoveHighlight) {
        notesEditor = new NotesEditor(onNotesChanged);
        highlightList = new HighlightList(onRemoveHighlight);

        addClassName("reader-side-panel");
        // Height only: the panel's width belongs to the stylesheet, which narrows it
        // to nothing and stacks it under the article on a phone.
        setHeightFull();

        Tab notesTab = new Tab(VaadinIcon.NOTEBOOK.create(), new Span(getTranslation("reader.tab.notes")));
        highlightsLabel.setText(getTranslation("reader.tab.highlights"));
        highlightsTab = new Tab(VaadinIcon.QUOTE_RIGHT.create(), highlightsLabel);

        add(notesTab, notesEditor);
        add(highlightsTab, highlightList);
    }

    public void show(Bookmark bookmark) {
        notesEditor.setNotes(bookmark.notes());
        highlightList.setHighlights(bookmark.highlights());
        highlightsLabel.setText(bookmark.hasHighlights()
                ? getTranslation("reader.tab.highlights.count", bookmark.highlights().size())
                : getTranslation("reader.tab.highlights"));
    }

    /**
     * Brings the kept passages into view, so that saving a highlight shows where it
     * went.
     */
    public void selectHighlights() {
        setSelectedTab(highlightsTab);
    }
}
