package io.binarycodes.harbor.library.ui.view;

import com.vaadin.flow.router.Route;

import io.binarycodes.harbor.base.ui.MainLayout;
import io.binarycodes.harbor.library.domain.LibraryScope;
import io.binarycodes.harbor.library.service.BookmarkService;
import io.binarycodes.harbor.library.service.LibraryFilter;
import io.binarycodes.harbor.library.ui.component.LibraryContent;

/**
 * The queue of bookmarks the reader set aside for when they have time.
 */
@Route(value = "later", layout = MainLayout.class)
public class ReadLaterView extends LibraryContent {

    public ReadLaterView(BookmarkService bookmarkService, LibraryFilter libraryFilter) {
        super(LibraryScope.READ_LATER, bookmarkService, libraryFilter);
    }
}
