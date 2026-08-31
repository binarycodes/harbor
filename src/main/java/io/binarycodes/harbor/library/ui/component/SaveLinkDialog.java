package io.binarycodes.harbor.library.ui.component;

import java.util.List;
import java.util.function.Consumer;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.value.ValueChangeMode;

import io.binarycodes.harbor.library.domain.Bookmark;
import io.binarycodes.harbor.library.domain.BookmarkType;
import io.binarycodes.harbor.library.domain.LinkDraft;
import io.binarycodes.harbor.library.service.AddressNotAllowedException;
import io.binarycodes.harbor.library.service.DuplicateBookmarkException;
import io.binarycodes.harbor.library.service.LinkMetadata;
import io.binarycodes.harbor.library.ui.presenter.LibraryPresenter;

/**
 * Saving a link: paste a URL, let Harbor read the page, correct anything it got
 * wrong, and file it.
 *
 * <p>Reading the page means waiting on a stranger's web server, so the fetch runs
 * off the UI thread and the dialog is pushed its result. Nothing can be saved until
 * that read succeeds and the page has been archived: a library of links whose pages
 * were never reachable is a library of guesses, and one whose pages were never
 * archived is a library of links that will rot.
 */
public class SaveLinkDialog extends Dialog {

    private final LibraryPresenter presenter;
    private final Consumer<Bookmark> onSaved;

    private final Binder<LinkDraft> binder = new Binder<>(LinkDraft.class);
    private final TextField url = new TextField();
    private final Button fetchButton = new Button();
    private final TextField title = new TextField();
    private final TextArea description = new TextArea();
    private final TagChipsField tags = new TagChipsField();
    private final Checkbox readLater = new Checkbox();
    private final Div review = new Div();
    private final Div reviewSummary = new Div();
    private final Button saveButton = new Button();

    private LinkDraft draft = new LinkDraft();
    private boolean fetching;

    /**
     * Whether the page behind the current URL was read and archived. Nothing can be
     * saved until both are true, so a link that cannot be fetched — or cannot be
     * archived — cannot be filed.
     */
    private boolean pageRead;

    /**
     * The bookmark being edited, or null when the dialog is saving a new link. It is
     * what tells {@link #commit()} whether to add or to overwrite.
     */
    private String editingId;

    public SaveLinkDialog(LibraryPresenter presenter, Consumer<Bookmark> onSaved) {
        this.presenter = presenter;
        this.onSaved = onSaved;

        addClassName("save-link-dialog");
        setHeaderTitle(getTranslation("save.title"));
        setWidth("520px");
        setDraggable(false);

        bindFields();
        add(body());
        getFooter().add(cancelButton(), submitButton());
    }

    /**
     * Reopening starts from a blank draft — a dialog that remembers the last link
     * you saved is a dialog that saves it twice.
     */
    public void openBlank() {
        editingId = null;
        draft = new LinkDraft();
        binder.setBean(draft);
        tags.setValue(List.of());
        setHeaderTitle(getTranslation("save.title"));
        saveButton.setText(getTranslation("save.submit"));
        showReview(false);
        pageRead = false;
        updateSubmitState();
        open();
        url.focus();
    }

    /**
     * The same dialog, opened over a bookmark that already exists. The review fields
     * are shown straight away because there is nothing to fetch before they can be
     * filled in — the page was read when it was saved.
     */
    public void openFor(Bookmark bookmark) {
        editingId = bookmark.id();
        draft = draftOf(bookmark);
        binder.setBean(draft);
        tags.setValue(bookmark.tags());
        setHeaderTitle(getTranslation("save.edit.title"));
        saveButton.setText(getTranslation("save.edit.submit"));
        showSummary(bookmark.site(), bookmark.type(), "save.edit.saved");
        showReview(true);
        pageRead = true;
        updateSubmitState();
        open();
        title.focus();
    }

    /**
     * Everything the dialog can edit, plus the fetched details it must carry across so
     * that saving without a re-fetch does not discard the article itself.
     */
    private static LinkDraft draftOf(Bookmark bookmark) {
        LinkDraft existing = new LinkDraft();
        existing.setUrl(bookmark.url());
        existing.setTitle(bookmark.title());
        existing.setDescription(bookmark.description());
        existing.setSite(bookmark.site());
        existing.setTags(bookmark.tags());
        existing.setType(bookmark.type());
        existing.setReadLater(bookmark.readLater());
        existing.setReadingMinutes(bookmark.readingMinutes());
        existing.setContent(bookmark.content());
        return existing;
    }

    private void bindFields() {
        binder.forField(url)
                .asRequired(getTranslation("save.url.required"))
                .bind(LinkDraft::getUrl, LinkDraft::setUrl);
        binder.forField(title).bind(LinkDraft::getTitle, LinkDraft::setTitle);
        binder.forField(description).bind(LinkDraft::getDescription, LinkDraft::setDescription);
        binder.forField(tags).bind(LinkDraft::tagsOrEmpty, LinkDraft::setTags);
        binder.forField(readLater).bind(LinkDraft::isReadLater, LinkDraft::setReadLater);
        binder.setBean(draft);
    }

    private Div body() {
        Span subtitle = new Span(getTranslation("save.subtitle"));
        subtitle.addClassName("save-link-subtitle");

        Div body = new Div(subtitle, urlBlock(), reviewSection());
        body.addClassName("save-link-body");
        return body;
    }

    private Div urlBlock() {
        url.setLabel(getTranslation("save.url"));
        url.setPlaceholder(getTranslation("save.url.placeholder"));
        url.setPrefixComponent(VaadinIcon.LINK.create());
        url.setClearButtonVisible(true);
        url.setValueChangeMode(ValueChangeMode.EAGER);
        url.setWidthFull();
        // What was fetched no longer describes what is typed, so the gate closes again.
        url.addValueChangeListener(event -> {
            if (pageRead) {
                draft.setSite(null);
                showReview(false);
                pageRead = false;
                updateSubmitState();
            }
        });

        fetchButton.addThemeVariants(ButtonVariant.PRIMARY);
        fetchButton.addClassName("save-link-fetch");
        fetchButton.addClickListener(event -> fetchMetadata());

        HorizontalLayout row = new HorizontalLayout(url, fetchButton);
        row.addClassName("save-link-url-row");
        row.setWidthFull();
        row.setAlignItems(HorizontalLayout.Alignment.END);
        row.setFlexGrow(1, url);

        Span hint = new Span(getTranslation("save.url.hint"));
        hint.addClassName("save-link-hint");

        Div block = new Div(row, hint);
        block.addClassName("save-link-url-block");
        return block;
    }

    private Div reviewSection() {
        reviewSummary.addClassName("save-link-summary");

        title.setLabel(getTranslation("save.field.title"));
        title.addClassName("save-link-title");
        title.setWidthFull();
        description.setLabel(getTranslation("save.field.description"));
        description.setWidthFull();
        tags.setLabel(getTranslation("save.tags"));
        tags.setWidthFull();
        readLater.setLabel(getTranslation("save.read_later"));

        Span readLaterHint = new Span(getTranslation("save.read_later.hint"));
        readLaterHint.addClassName("save-link-hint");

        review.addClassName("save-link-review");
        review.add(reviewSummary, title, description, tags, readLater, readLaterHint);
        review.setVisible(false);
        return review;
    }

    private Button cancelButton() {
        Button cancel = new Button(getTranslation("save.cancel"), event -> close());
        cancel.addClassName("save-link-cancel");
        return cancel;
    }

    private Button submitButton() {
        saveButton.setText(getTranslation("save.submit"));
        saveButton.setIcon(VaadinIcon.BOOKMARK.create());
        saveButton.addThemeVariants(ButtonVariant.PRIMARY);
        saveButton.addClassName("save-link-submit");
        saveButton.addClickListener(event -> save());
        return saveButton;
    }

    private void fetchMetadata() {
        if (fetching || !binder.validate().isOk()) {
            return;
        }
        resolveInBackground(this::reviewFetched);
    }

    /**
     * Two ways this can come back with nothing worth saving, and they are different
     * problems for the reader: the page could not be reached at all, or it was
     * reached and could not be archived. Saying which is the difference between
     * "check the link" and "the archiver is down".
     *
     * <p>The second is only a refusal where the deployment renders before saving.
     * Where it does not, an empty archive is what every fetch returns — the render has
     * not been attempted yet — and refusing on it would mean nothing could ever be
     * saved.
     */
    private void reviewFetched(LinkMetadata metadata) {
        if (!metadata.pageRead()) {
            refuse("save.url.unreadable");
            return;
        }
        if (!metadata.hasArchive() && presenter.isArchiveRequiredBeforeSave()) {
            refuse("save.url.unarchivable");
            return;
        }
        applyMetadata(metadata);
        showReview(true);
        pageRead = true;
        updateSubmitState();
    }

    private void refuse(String reasonKey) {
        pageRead = false;
        showReview(false);
        url.setErrorMessage(getTranslation(reasonKey));
        url.setInvalid(true);
        updateSubmitState();
    }

    /**
     * Saving never fetches on the reader's behalf: the page has to have been read and
     * archived first, which is what stops an unreachable link being filed as if it
     * were an article. An edit is already past that gate unless its URL changed.
     */
    private void save() {
        if (fetching || !pageRead || !binder.validate().isOk()) {
            return;
        }
        commit();
    }

    private void updateSubmitState() {
        saveButton.setEnabled(!fetching && pageRead);
    }

    /**
     * The callback reports the stored bookmark whether it was just created or just
     * edited, and what that means is the owner's decision: the sidebar's dialog opens
     * a newly saved link in the reader, while the reader's own redraws the article it
     * is already showing.
     */
    private void commit() {
        try {
            if (editingId != null) {
                presenter.update(editingId, draft);
                close();
                presenter.findById(editingId).ifPresent(onSaved);
                return;
            }
            Bookmark saved = presenter.add(draft);
            close();
            onSaved.accept(saved);
        } catch (DuplicateBookmarkException alreadySaved) {
            reportDuplicate(alreadySaved);
        }
    }

    /**
     * The dialog stays open on the URL that clashed: the reader is one edit away from
     * saving a different link, and closing would lose everything they had typed.
     */
    private void reportDuplicate(DuplicateBookmarkException alreadySaved) {
        url.setErrorMessage(getTranslation("save.url.duplicate", alreadySaved.getExisting().title()));
        url.setInvalid(true);
    }

    /**
     * The read itself belongs to the presenter — waiting on a stranger's web server
     * and coming back through the session lock is orchestration, not something a
     * form should be doing. What is left here is the spinner either outcome clears.
     */
    private void resolveInBackground(Consumer<LinkMetadata> onResolved) {
        UI ui = getUI().orElseThrow(() -> new IllegalStateException("The dialog is not attached"));
        setFetching(true);
        presenter.resolve(ui, draft.getUrl(),
                metadata -> {
                    setFetching(false);
                    onResolved.accept(metadata);
                },
                failure -> {
                    setFetching(false);
                    reportFailure(failure);
                });
    }

    /**
     * The resolver describes an unreachable page from its URL instead of failing, so
     * the only failure that arrives here is one worth showing: an address this
     * deployment refuses to fetch. Nothing is saved, and the URL keeps the message
     * until it changes.
     *
     * <p>The message names neither the address nor the property that would permit it.
     * A reader being signed in does not make them the operator, and what the server can
     * reach or how its guard is configured is the operator's to know; the detail goes
     * to the log, where they will look.
     */
    private void reportFailure(Throwable failure) {
        if (failure instanceof AddressNotAllowedException) {
            url.setErrorMessage(getTranslation("save.url.blocked"));
            url.setInvalid(true);
        }
    }

    /**
     * The page's own description of itself fills in only what the reader has not
     * already written, so a re-fetch never overwrites their edits.
     */
    private void applyMetadata(LinkMetadata metadata) {
        draft.setSite(metadata.site());
        draft.setType(metadata.type());
        draft.setReadingMinutes(metadata.readingMinutes());
        draft.setContent(metadata.content());
        draft.setArchive(metadata.archive());
        draft.setRefetched(true);
        if (isBlank(draft.getTitle())) {
            draft.setTitle(metadata.title());
        }
        if (isBlank(draft.getDescription())) {
            draft.setDescription(metadata.description());
        }
        if (draft.tagsOrEmpty().isEmpty()) {
            draft.setTags(metadata.tags());
        }
        binder.setBean(draft);
        showSummary(metadata.site(), metadata.type(), "save.fetched");
    }

    private void showSummary(String site, BookmarkType type, String statusKey) {
        Span status = new Span(VaadinIcon.CHECK.create(), new Span(getTranslation(statusKey)));
        status.addClassName("save-link-fetched");
        Span origin = new Span(getTranslation("save.origin", site, getTranslation(type.translationKey())));
        origin.addClassName("save-link-origin");

        reviewSummary.removeAll();
        reviewSummary.add(CoverTile.forSite(site), new Div(status, origin));
    }

    private void showReview(boolean visible) {
        review.setVisible(visible);
        fetchButton.setText(getTranslation(visible ? "save.refetch" : "save.fetch"));
    }

    private void setFetching(boolean inProgress) {
        fetching = inProgress;
        fetchButton.setEnabled(!inProgress);
        updateSubmitState();
        if (inProgress) {
            Icon spinner = VaadinIcon.SPINNER.create();
            spinner.addClassName("save-link-spinner");
            fetchButton.setIcon(spinner);
            fetchButton.setText(getTranslation("save.fetching"));
        } else {
            fetchButton.setIcon(null);
            fetchButton.setText(getTranslation(review.isVisible() ? "save.refetch" : "save.fetch"));
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
