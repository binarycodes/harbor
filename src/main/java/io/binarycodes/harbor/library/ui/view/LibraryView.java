package io.binarycodes.harbor.library.ui.view;

import jakarta.annotation.security.PermitAll;

import com.vaadin.flow.router.Route;

import io.binarycodes.harbor.base.ui.MainLayout;
import io.binarycodes.harbor.library.domain.LibraryScope;
import io.binarycodes.harbor.library.ui.component.LibraryContent;
import io.binarycodes.harbor.library.ui.presenter.LibraryFilter;
import io.binarycodes.harbor.library.ui.presenter.LibraryPresenter;

/**
 * Everything the reader has saved.
 */
@PermitAll
@Route(value = "", layout = MainLayout.class)
public class LibraryView extends LibraryContent {

    public LibraryView(LibraryPresenter presenter, LibraryFilter libraryFilter) {
        super(LibraryScope.ALL, presenter, libraryFilter);
    }
}
