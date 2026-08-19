package io.binarycodes.harbor.library.ui.view;

import jakarta.annotation.security.PermitAll;

import com.vaadin.flow.router.Route;

import io.binarycodes.harbor.base.ui.MainLayout;
import io.binarycodes.harbor.library.domain.LibraryScope;
import io.binarycodes.harbor.library.ui.component.LibraryContent;
import io.binarycodes.harbor.library.ui.presenter.LibraryFilter;
import io.binarycodes.harbor.library.ui.presenter.LibraryPresenter;

/**
 * The queue of bookmarks the reader set aside for when they have time.
 */
@PermitAll
@Route(value = "later", layout = MainLayout.class)
public class ReadLaterView extends LibraryContent {

    public ReadLaterView(LibraryPresenter presenter, LibraryFilter libraryFilter) {
        super(LibraryScope.READ_LATER, presenter, libraryFilter);
    }
}
