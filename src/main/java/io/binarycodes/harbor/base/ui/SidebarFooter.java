package io.binarycodes.harbor.base.ui;

import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.security.AuthenticationContext;

import io.binarycodes.harbor.library.ui.presenter.LibraryPresenter;

/**
 * The foot of the drawer: how much is in the library, and the reader it belongs to.
 *
 * <p>One row rather than two. The library and the account are the same fact from two
 * sides — this library is this reader's — and the appearance and sign-out controls that
 * used to sit on their own rows are a click away behind the avatar instead, where the
 * things that are about the reader belong together.
 */
public class SidebarFooter extends HorizontalLayout {

    private final LibraryPresenter presenter;
    private final Span itemCount = new Span();
    private final AccountMenu accountMenu;

    public SidebarFooter(LibraryPresenter presenter, AuthenticationContext authenticationContext) {
        this.presenter = presenter;

        addClassName("sidebar-footer");
        setPadding(false);
        setAlignItems(Alignment.CENTER);
        setWidthFull();

        Span indicator = new Span();
        indicator.addClassName("sidebar-footer-indicator");

        Span heading = new Span(getTranslation("sidebar.storage"));
        heading.addClassName("sidebar-footer-heading");
        itemCount.addClassName("sidebar-footer-count");

        VerticalLayout status = new VerticalLayout(heading, itemCount);
        status.addClassName("sidebar-footer-status");
        status.setPadding(false);
        status.setSpacing(false);

        Avatar avatar = new Avatar();
        accountMenu = new AccountMenu(avatar, presenter, authenticationContext);

        // The menu renders nothing where it is added — it is positioned against the
        // avatar it targets — but it is a component, so it needs a place in the tree.
        add(indicator, status, avatar, accountMenu);
        setFlexGrow(1, status);
    }

    public void refresh() {
        int count = presenter.count();
        itemCount.setText(count == 1
                ? getTranslation("sidebar.storage.items.one")
                : getTranslation("sidebar.storage.items.many", count));
        accountMenu.refresh();
    }
}
