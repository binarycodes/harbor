package io.binarycodes.harbor.library.ui.component;

import java.util.List;
import java.util.function.Consumer;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.markdown.Markdown;

import io.binarycodes.harbor.library.domain.Bookmark;
import io.binarycodes.harbor.library.domain.Highlight;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * The article itself: its tags, title, byline, and body.
 *
 * <p>Selecting text raises the highlight button. The selection is read from the
 * browser through event data rather than a script module, so a passage can be kept
 * without any client-side code of our own. Passages already kept are marked in the
 * rendered body afterwards, which has to happen in the browser because the
 * Markdown component sanitises anything we would otherwise inject as HTML.
 */
public class ReaderArticle extends Div {

    private static final String SELECTION_TEXT = "window.getSelection().toString()";
    private static final String SELECTION_CENTER_X =
            "window.getSelection().rangeCount ? window.getSelection().getRangeAt(0).getBoundingClientRect().left"
                    + " + window.getSelection().getRangeAt(0).getBoundingClientRect().width / 2 : 0";
    private static final String SELECTION_TOP =
            "window.getSelection().rangeCount"
                    + " ? window.getSelection().getRangeAt(0).getBoundingClientRect().top : 0";

    private static final int SHORTEST_HIGHLIGHT = 3;

    /**
     * Wraps each kept passage in a {@code <mark>} at its first occurrence. Existing
     * marks are unwrapped first, so the same script also handles a passage the
     * reader just deleted. The Markdown component renders into its own light DOM,
     * which is both where the text is and why the marks can be styled from the
     * application stylesheet.
     */
    private static final String MARK_HIGHLIGHTS = """
            const passages = $0;
            const element = this;
            Promise.resolve(this.updateComplete).then(() => {
              element.querySelectorAll('mark.reader-mark').forEach((mark) => {
                const parent = mark.parentNode;
                while (mark.firstChild) parent.insertBefore(mark.firstChild, mark);
                parent.removeChild(mark);
                parent.normalize();
              });
              for (const passage of passages) {
                if (!passage) continue;
                const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT);
                let node;
                while ((node = walker.nextNode())) {
                  if (node.parentElement && node.parentElement.closest('mark')) continue;
                  const start = node.nodeValue.indexOf(passage);
                  if (start < 0) continue;
                  const range = document.createRange();
                  range.setStart(node, start);
                  range.setEnd(node, start + passage.length);
                  const mark = document.createElement('mark');
                  mark.className = 'reader-mark';
                  range.surroundContents(mark);
                  break;
                }
              }
            });
            """;

    /**
     * Sends every link in the article out to a new tab. The anchors are created in
     * the browser by the Markdown renderer, so there is no server-side element to
     * configure; {@code router-ignore} keeps Flow's client router from claiming a
     * link that happens to look like one of our own routes.
     */
    private static final String OPEN_LINKS_IN_NEW_TAB = """
            const element = this;
            Promise.resolve(this.updateComplete).then(() => {
              element.querySelectorAll('a[href]').forEach((link) => {
                link.setAttribute('target', '_blank');
                link.setAttribute('rel', 'noopener');
                link.setAttribute('router-ignore', '');
              });
            });
            """;

    private final Div tagSlot = new Div();
    private final H1 title = new H1();
    private final Div byline = new Div();
    private final Markdown body = new Markdown();
    private final SelectionHighlightButton highlightButton;

    private String selectedText = "";

    public ReaderArticle(Consumer<String> onHighlightRequested) {
        highlightButton = new SelectionHighlightButton(() -> {
            onHighlightRequested.accept(selectedText);
            clearSelection();
        });

        addClassName("reader-article");
        title.addClassName("reader-title");
        byline.addClassName("reader-byline");
        body.getElement().getClassList().add("reader-body");
        add(tagSlot, title, byline, body, highlightButton);

        listenForSelection();
    }

    public void show(Bookmark bookmark) {
        tagSlot.removeAll();
        tagSlot.add(new TagChips(bookmark.tags()));
        title.setText(bookmark.title());

        byline.removeAll();
        byline.add(bylineItem(VaadinIcon.USER, bookmark.author()),
                bylineItem(VaadinIcon.CLOCK, getTranslation("reader.reading_time", bookmark.readingMinutes())),
                bylineItem(VaadinIcon.CALENDAR,
                        getTranslation("reader.saved", RelativeDate.label(this, bookmark.savedAt()))));

        // A page we could not read leaves the reader with nothing but the link out.
        body.setContent(bookmark.content().isBlank()
                ? getTranslation("reader.body.empty")
                : bookmark.content());
        markHighlights(bookmark.highlights());
        body.getElement().executeJs(OPEN_LINKS_IN_NEW_TAB);
        highlightButton.hide();
    }

    private void markHighlights(List<Highlight> highlights) {
        ArrayNode passages = JsonNodeFactory.instance.arrayNode();
        highlights.forEach(highlight -> passages.add(highlight.text()));
        body.getElement().executeJs(MARK_HIGHLIGHTS, passages);
    }

    private void listenForSelection() {
        getElement().addEventListener("mouseup", event -> {
            String text = event.getEventData().get(SELECTION_TEXT).asString().replaceAll("\\s+", " ").strip();
            if (text.length() < SHORTEST_HIGHLIGHT) {
                selectedText = "";
                highlightButton.hide();
                return;
            }
            selectedText = text;
            highlightButton.showAt(event.getEventData().get(SELECTION_CENTER_X).asDouble(),
                    event.getEventData().get(SELECTION_TOP).asDouble());
        })
                .addEventData(SELECTION_TEXT)
                .addEventData(SELECTION_CENTER_X)
                .addEventData(SELECTION_TOP);
    }

    private void clearSelection() {
        selectedText = "";
        highlightButton.hide();
        getElement().executeJs("window.getSelection().removeAllRanges();");
    }

    private Span bylineItem(VaadinIcon icon, String text) {
        Span item = new Span(icon.create(), new Span(text));
        item.addClassName("reader-byline-item");
        return item;
    }
}
