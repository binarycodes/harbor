package io.binarycodes.harbor.base.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.annotation.security.PermitAll;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.shared.Registration;
import com.vaadin.flow.spring.security.AuthenticationContext;

import io.binarycodes.harbor.library.domain.Bookmark;
import io.binarycodes.harbor.library.domain.TagCount;
import io.binarycodes.harbor.library.ui.component.SaveLinkDialog;
import io.binarycodes.harbor.library.ui.presenter.LibraryFilter;
import io.binarycodes.harbor.library.ui.presenter.LibraryPresenter;
import io.binarycodes.harbor.library.ui.view.ReaderView;

/**
 * The application shell. The drawer carries navigation and the tag filters; the
 * views render beside it.
 *
 * <p>The library is only known once the browser has answered with what it has
 * stored, so the sidebar fills itself in from a change listener rather than at
 * construction.
 *
 * <p>Annotated in its own right, and not redundantly: navigation access control checks
 * every parent layout a route names as well as the route, and denies the route when the
 * layout is the less permissive of the two. An unannotated shell therefore turns every
 * screen behind it into a RouteNotFoundError.
 */
@PermitAll
public class MainLayout extends AppLayout {

    private final LibraryPresenter presenter;
    private final LibraryFilter libraryFilter;
    private final LibraryNavigation navigation = new LibraryNavigation();
    private final TagFilterList tagFilters;
    private final StorageFooter storageFooter;
    private final AccountFooter accountFooter;
    private final SaveLinkDialog saveLinkDialog;
    private final List<Registration> registrations = new ArrayList<>();

    private boolean importReported;

    public MainLayout(LibraryPresenter presenter, LibraryFilter libraryFilter,
            AuthenticationContext authenticationContext) {
        this.presenter = presenter;
        this.libraryFilter = libraryFilter;
        tagFilters = new TagFilterList(presenter, libraryFilter);
        storageFooter = new StorageFooter(presenter);
        accountFooter = new AccountFooter(authenticationContext);
        saveLinkDialog = new SaveLinkDialog(presenter, this::openReader);

        setPrimarySection(Section.DRAWER);
        addToNavbar(new DrawerToggle());
        addToDrawer(sidebar());
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        registrations.add(presenter.addChangeListener(this::onLibraryChanged));
        registrations.add(presenter.addConflictListener(this::reportConflict));
        registrations.add(libraryFilter.addChangeListener(this::refreshSidebar));
        presenter.load();
        refreshSidebar();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        registrations.forEach(Registration::remove);
        registrations.clear();
        super.onDetach(detachEvent);
    }

    private VerticalLayout sidebar() {
        VerticalLayout sidebar = new VerticalLayout(new SidebarBrand(), saveLinkButton(), navigation,
                tagFilters, storageFooter, accountFooter);
        sidebar.addClassName("sidebar");
        sidebar.setSizeFull();
        sidebar.setPadding(false);
        sidebar.setSpacing(false);
        sidebar.setFlexGrow(1, tagFilters);
        return sidebar;
    }

    private Button saveLinkButton() {
        Button saveLink = new Button(getTranslation("sidebar.save_link"), VaadinIcon.PLUS.create(),
                event -> saveLinkDialog.openBlank());
        saveLink.addThemeVariants(ButtonVariant.PRIMARY);
        saveLink.addClassName("save-link-button");
        return saveLink;
    }

    private void openReader(Bookmark saved) {
        UI.getCurrent().navigate(ReaderView.class, new RouteParameters(ReaderView.BOOKMARK_ID, saved.id()));
    }

    /**
     * A deleted bookmark can take the last use of a tag with it, so a selection
     * that no longer matches anything is dropped before the sidebar redraws.
     */
    private void onLibraryChanged() {
        libraryFilter.retainTags(knownTags());
        refreshSidebar();
        reportImportedLibrary();
    }

    /**
     * A library carried over from this browser's own storage is worth saying out
     * loud once. Staying quiet about it looks exactly like the data loss the
     * import exists to prevent.
     */
    private void reportImportedLibrary() {
        int imported = presenter.importedFromBrowser();
        if (imported == 0 || importReported) {
            return;
        }
        importReported = true;
        Notification.show(getTranslation(
                imported == 1 ? "library.imported.one" : "library.imported.many", imported));
    }

    /**
     * One reader can still be in two places — a second tab, a phone — and edit the
     * same bookmark from both. The one who loses has to be told: their change is
     * gone, and the screen has just redrawn with the other edit.
     */
    private void reportConflict() {
        Notification.show(getTranslation("library.conflict"));
    }

    private void refreshSidebar() {
        navigation.refresh(presenter.count(), presenter.countReadLater(),
                presenter.countHighlights());
        tagFilters.refresh();
        storageFooter.refresh();
    }

    private Set<String> knownTags() {
        return presenter.tagCounts().stream().map(TagCount::name).collect(Collectors.toSet());
    }
}
