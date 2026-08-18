package io.binarycodes.harbor.library.ui.component;

import java.util.function.Consumer;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;

import io.binarycodes.harbor.library.domain.Highlight;
import io.binarycodes.harbor.library.domain.HighlightGroup;

/**
 * Everything the reader kept from one page, under a heading that takes them back
 * to where the passages came from.
 */
public class HighlightGroupCard extends Div {

    public HighlightGroupCard(HighlightGroup bookmark, Consumer<HighlightGroup> onOpen) {
        addClassName("highlight-group");
        add(heading(bookmark, onOpen), passages(bookmark));
    }

    private NativeButton heading(HighlightGroup bookmark, Consumer<HighlightGroup> onOpen) {
        Span title = new Span(bookmark.title());
        title.addClassName("highlight-group-title");
        int count = bookmark.highlights().size();
        String passages = count == 1
                ? getTranslation("highlights.count.one")
                : getTranslation("highlights.count.many", count);
        Span origin = new Span(getTranslation("highlights.group.origin", bookmark.site(), passages));
        origin.addClassName("highlight-group-origin");

        NativeButton heading = new NativeButton();
        heading.addClassName("highlight-group-heading");
        heading.add(CoverTile.forSite(bookmark.site()), new Div(title, origin));
        heading.addClickListener(event -> onOpen.accept(bookmark));
        return heading;
    }

    private Div passages(HighlightGroup bookmark) {
        Div passages = new Div();
        passages.addClassName("highlight-group-passages");
        bookmark.highlights().stream().map(Highlight::text).forEach(text -> {
            Paragraph passage = new Paragraph(text);
            passage.addClassName("highlight-passage");
            passages.add(passage);
        });
        return passages;
    }
}
