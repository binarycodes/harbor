package io.binarycodes.harbor.base.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import io.binarycodes.harbor.library.domain.TagCount;
import io.binarycodes.harbor.library.service.BookmarkService;
import io.binarycodes.harbor.library.service.LibraryFilter;

/**
 * Every tag in the library as a toggle, most used first. Selecting several
 * narrows the listing to bookmarks carrying all of them, which is why the header
 * offers a way out of the combination in one click.
 *
 * <p>The toggles are native buttons rather than Vaadin Buttons because each holds
 * three pieces of content — a color dot, the name, and a count — and a Vaadin
 * Button takes only an icon and a label.
 */
public class TagFilterList extends VerticalLayout {

    private final BookmarkService bookmarkService;
    private final LibraryFilter libraryFilter;
    private final Button clearButton;
    private final Div tags = new Div();

    public TagFilterList(BookmarkService bookmarkService, LibraryFilter libraryFilter) {
        this.bookmarkService = bookmarkService;
        this.libraryFilter = libraryFilter;

        addClassName("tag-filter-list");
        setPadding(false);
        setSpacing(false);

        Span heading = new Span(getTranslation("sidebar.tags"));
        heading.addClassName("tag-filter-heading");

        clearButton = new Button(getTranslation("sidebar.tags.clear"), event -> libraryFilter.clearTags());
        clearButton.addThemeVariants(ButtonVariant.TERTIARY);
        clearButton.addClassName("tag-filter-clear");

        HorizontalLayout header = new HorizontalLayout(heading, clearButton);
        header.addClassName("tag-filter-header");
        header.setPadding(false);
        header.setAlignItems(Alignment.CENTER);
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        header.setWidthFull();

        tags.addClassName("tag-filter-tags");

        add(header, tags);
    }

    public void refresh() {
        clearButton.setVisible(libraryFilter.hasSelectedTags());
        tags.removeAll();
        bookmarkService.tagCounts().forEach(tag -> tags.add(toggle(tag)));
        setVisible(!bookmarkService.tagCounts().isEmpty());
    }

    private NativeButton toggle(TagCount tag) {
        Span dot = new Span();
        dot.addClassName("tag-filter-dot");
        dot.getStyle().set("background", "var(--color-cover-" + tag.colorIndex() + ")");

        Span name = new Span(tag.name());
        name.addClassName("tag-filter-name");

        Span count = new Span(String.valueOf(tag.count()));
        count.addClassName("tag-filter-count");

        NativeButton toggle = new NativeButton();
        toggle.addClassName("tag-filter-tag");
        toggle.add(dot, name, count);
        toggle.addClickListener(event -> libraryFilter.toggleTag(tag.name()));

        boolean selected = libraryFilter.isSelected(tag.name());
        toggle.setClassName("selected", selected);
        toggle.getElement().setAttribute("aria-pressed", String.valueOf(selected));
        return toggle;
    }
}
