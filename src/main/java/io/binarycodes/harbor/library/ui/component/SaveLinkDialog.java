package io.binarycodes.harbor.library.ui.component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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
import io.binarycodes.harbor.library.domain.LinkDraft;
import io.binarycodes.harbor.library.service.AddressNotAllowedException;
import io.binarycodes.harbor.library.service.BookmarkService;
import io.binarycodes.harbor.library.service.LinkMetadata;
import io.binarycodes.harbor.library.service.MetadataResolver;

/**
 * Saving a link: paste a URL, let Harbor read the page, correct anything it got
 * wrong, and file it.
 *
 * <p>Reading the page means waiting on a stranger's web server, so the fetch runs
 * off the UI thread and the dialog is pushed its result. Saving without fetching
 * first is allowed and does the fetch on the way, because the reader's intent was
 * to save the link either way.
 */
public class SaveLinkDialog extends Dialog {

    private final BookmarkService bookmarkService;
    private final MetadataResolver metadataResolver;
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

    public SaveLinkDialog(BookmarkService bookmarkService, MetadataResolver metadataResolver,
            Consumer<Bookmark> onSaved) {
        this.bookmarkService = bookmarkService;
        this.metadataResolver = metadataResolver;
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
        draft = new LinkDraft();
        binder.setBean(draft);
        tags.setValue(List.of());
        showReview(false);
        open();
        url.focus();
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
        // What was fetched no longer describes what is typed.
        url.addValueChangeListener(event -> {
            if (review.isVisible()) {
                draft.setSite(null);
                showReview(false);
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
        resolveInBackground(metadata -> {
            applyMetadata(metadata);
            showReview(true);
        });
    }

    private void save() {
        if (fetching || !binder.validate().isOk()) {
            return;
        }
        if (draft.getSite() == null || draft.getSite().isBlank()) {
            resolveInBackground(metadata -> {
                applyMetadata(metadata);
                commit();
            });
            return;
        }
        commit();
    }

    private void commit() {
        Bookmark saved = bookmarkService.add(draft);
        close();
        onSaved.accept(saved);
    }

    /**
     * Runs the resolver away from the UI thread and hands the result back through
     * the session lock. Push is enabled for exactly this.
     */
    private void resolveInBackground(Consumer<LinkMetadata> onResolved) {
        UI ui = getUI().orElseThrow(() -> new IllegalStateException("The dialog is not attached"));
        String requested = draft.getUrl();
        setFetching(true);
        CompletableFuture
                .supplyAsync(() -> metadataResolver.resolve(requested))
                .whenComplete((metadata, failure) -> ui.access(() -> {
                    setFetching(false);
                    if (metadata != null) {
                        onResolved.accept(metadata);
                        return;
                    }
                    reportFailure(failure);
                }));
    }

    /**
     * The resolver describes an unreachable page from its URL instead of failing, so
     * the only failure that arrives here is one worth showing: an address this
     * deployment refuses to fetch. Nothing is saved, and the URL keeps the message
     * until it changes.
     *
     * <p>The message names neither the address nor the property that would permit it.
     * Harbor has no accounts, so whoever pasted the link is not necessarily someone
     * who should be told what the server can see or how its guard is configured; the
     * detail goes to the log, where the operator will look.
     */
    private void reportFailure(Throwable failure) {
        Throwable cause = failure instanceof CompletionException ? failure.getCause() : failure;
        if (cause instanceof AddressNotAllowedException) {
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

        Span fetched = new Span(VaadinIcon.CHECK.create(), new Span(getTranslation("save.fetched")));
        fetched.addClassName("save-link-fetched");
        Span origin = new Span(getTranslation("save.origin", metadata.site(),
                getTranslation(metadata.type().translationKey())));
        origin.addClassName("save-link-origin");

        reviewSummary.removeAll();
        reviewSummary.add(CoverTile.forSite(metadata.site()), new Div(fetched, origin));
    }

    private void showReview(boolean visible) {
        review.setVisible(visible);
        fetchButton.setText(getTranslation(visible ? "save.refetch" : "save.fetch"));
    }

    private void setFetching(boolean inProgress) {
        fetching = inProgress;
        fetchButton.setEnabled(!inProgress);
        saveButton.setEnabled(!inProgress);
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
