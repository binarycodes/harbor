package io.binarycodes.harbor.library.ui.component;

import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.card.Card;
import com.vaadin.flow.component.card.CardVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;

import io.binarycodes.harbor.library.domain.BookmarkSummary;

/**
 * A bookmark as a picture card: cover tile, title, where it came from, the
 * opening lines, its tags, and what the reader has added to it.
 */
public class BookmarkCard extends Card {

    public BookmarkCard(BookmarkSummary bookmark, BookmarkActions actions) {
        getElement().getClassList().add("bookmark-card");
        addThemeVariants(CardVariant.OUTLINED, CardVariant.STRETCH_MEDIA, CardVariant.COVER_MEDIA);
        setAriaRole("listitem");
        setTitleHeadingLevel(3);

        setMedia(cover(bookmark, actions));
        Span title = new Span(bookmark.title());
        title.addClassName("bookmark-card-title");
        setTitle(title);
        setSubtitle(new Span(getTranslation("bookmark.origin", bookmark.site(),
                RelativeDate.label(this, bookmark.savedAt()))));

        Paragraph description = new Paragraph(bookmark.description());
        description.addClassName("bookmark-card-description");
        add(description, new TagChips(bookmark.tags()));
        addToFooter(new BookmarkMetadata(bookmark), actions(bookmark, actions));

        getElement().addEventListener("click", event -> actions.open(bookmark));
    }

    /**
     * The two controls travel as one footer child rather than two. Card's footer is a
     * flex row with a gap wide enough to separate the metadata from them, and as
     * separate children they would wear that same gap between themselves.
     */
    private Div actions(BookmarkSummary bookmark, BookmarkActions actions) {
        Div group = new Div(new EditBookmarkButton(() -> actions.edit(bookmark)),
                new DeleteBookmarkButton(() -> actions.remove(bookmark)));
        group.addClassName("bookmark-actions");
        return group;
    }

    private Div cover(BookmarkSummary bookmark, BookmarkActions actions) {
        Badge type = new Badge(getTranslation(bookmark.type().translationKey()));
        type.addClassName("bookmark-card-type");

        Div cover = new Div(CoverTile.forSite(bookmark.site()), type,
                new ReadLaterButton(bookmark, actions));
        cover.addClassName("bookmark-card-cover");
        return cover;
    }

    /**
     * The footer line: how long the page takes to read, plus markers for the
     * reader's own additions.
     */
    private static class BookmarkMetadata extends Div {

        BookmarkMetadata(BookmarkSummary bookmark) {
            addClassName("bookmark-metadata");
            add(item(VaadinIcon.CLOCK, getTranslation("bookmark.reading_time", bookmark.readingMinutes())));
            if (bookmark.hasHighlights()) {
                Span highlights = item(VaadinIcon.QUOTE_RIGHT, String.valueOf(bookmark.highlightCount()));
                highlights.addClassName("bookmark-metadata-highlights");
                add(highlights);
            }
            if (bookmark.hasNotes()) {
                Span notes = item(VaadinIcon.NOTEBOOK, getTranslation("bookmark.has_notes"));
                notes.addClassName("bookmark-metadata-notes");
                add(notes);
            }
        }

        private Span item(VaadinIcon icon, String text) {
            Span item = new Span(icon.create(), new Span(text));
            item.addClassName("bookmark-metadata-item");
            return item;
        }
    }
}
