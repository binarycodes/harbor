package io.binarycodes.harbor.base.ui;

import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.badge.BadgeVariant;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;

import io.binarycodes.harbor.library.ui.view.HighlightsView;
import io.binarycodes.harbor.library.ui.view.LibraryView;
import io.binarycodes.harbor.library.ui.view.ReadLaterView;

/**
 * The three places to go in the drawer, each carrying how much is in it.
 */
public class LibraryNavigation extends SideNav {

    private final Badge allCount = counter("nav.all");
    private final Badge readLaterCount = counter("nav.read_later");
    private final Badge highlightCount = counter("nav.highlights");

    public LibraryNavigation() {
        addClassName("library-navigation");

        SideNavItem all = new SideNavItem(getTranslation("nav.all"), LibraryView.class,
                VaadinIcon.BOOKMARK.create());
        all.setSuffixComponent(allCount);

        SideNavItem readLater = new SideNavItem(getTranslation("nav.read_later"), ReadLaterView.class,
                VaadinIcon.CLOCK.create());
        readLater.setSuffixComponent(readLaterCount);

        SideNavItem highlights = new SideNavItem(getTranslation("nav.highlights"), HighlightsView.class,
                VaadinIcon.QUOTE_RIGHT.create());
        highlights.setSuffixComponent(highlightCount);

        addItem(all, readLater, highlights);
    }

    public void refresh(int all, int readLater, int highlights) {
        allCount.setNumber(all);
        readLaterCount.setNumber(readLater);
        highlightCount.setNumber(highlights);
    }

    private Badge counter(String labelKey) {
        Badge badge = new Badge(getTranslation(labelKey), 0);
        badge.addThemeVariants(BadgeVariant.NUMBER_ONLY);
        return badge;
    }
}
