package io.binarycodes.harbor.library.ui.component;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.AnchorTarget;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.RouterLink;

import io.binarycodes.harbor.library.domain.Bookmark;
import io.binarycodes.harbor.library.ui.view.LibraryView;

/**
 * The reader's top bar: the way back to the library, where the article came from,
 * and the things worth doing to a whole article — queueing it, correcting its
 * details, deleting it, and leaving for the original.
 */
public class ReaderHeader extends HorizontalLayout {

    private final Span site = new Span();
    private final Button readLaterToggle = new Button();
    private final Anchor original = new Anchor();

    public ReaderHeader(Runnable onToggleReadLater, Runnable onEdit, Runnable onDelete) {
        addClassName("reader-header");
        setWidthFull();
        setAlignItems(Alignment.CENTER);

        RouterLink back = new RouterLink(LibraryView.class);
        back.addClassName("reader-back");
        back.add(VaadinIcon.ARROW_LEFT.create(), new Span(getTranslation("reader.back")));

        Span origin = new Span(VaadinIcon.GLOBE.create(), site);
        origin.addClassName("reader-origin");

        readLaterToggle.addThemeVariants(ButtonVariant.TERTIARY);
        readLaterToggle.addClassName("reader-read-later");
        readLaterToggle.addClickListener(event -> onToggleReadLater.run());

        // Leaving for the original must never cost the reader their place in Harbor:
        // a new tab every time, and the click handled by the browser rather than by
        // Flow's client-side router, which is otherwise attached to every anchor.
        original.addClassName("reader-original");
        original.setTarget(AnchorTarget.BLANK);
        original.setRouterIgnore(true);
        original.getElement().setAttribute("rel", "noopener");
        original.add(VaadinIcon.EXTERNAL_LINK.create(), new Span(getTranslation("reader.open_original")));

        EditBookmarkButton edit = new EditBookmarkButton(onEdit);
        edit.addClassName("reader-edit");
        edit.showLabel();

        DeleteBookmarkButton delete = new DeleteBookmarkButton(onDelete);
        delete.addClassName("reader-delete");
        delete.showLabel();

        HorizontalLayout actions = new HorizontalLayout(readLaterToggle, edit, delete, original);
        actions.addClassName("reader-actions");
        actions.setPadding(false);
        actions.setAlignItems(Alignment.CENTER);

        add(back, origin, actions);
        setFlexGrow(1, origin);
    }

    public void show(Bookmark bookmark) {
        site.setText(bookmark.site());
        original.setHref(bookmark.url());
        readLaterToggle.setIcon(bookmark.readLater()
                ? VaadinIcon.BOOKMARK.create()
                : VaadinIcon.BOOKMARK_O.create());
        readLaterToggle.setText(getTranslation(bookmark.readLater()
                ? "reader.read_later.queued"
                : "reader.read_later.add"));
        readLaterToggle.setClassName("queued", bookmark.readLater());
    }
}
