package io.binarycodes.harbor.library.ui.view;

import com.vaadin.flow.router.Route;

import io.binarycodes.harbor.base.ui.MainLayout;
import io.binarycodes.harbor.library.domain.LibraryScope;
import io.binarycodes.harbor.library.service.BookmarkService;
import io.binarycodes.harbor.library.service.LibraryFilter;
import io.binarycodes.harbor.library.service.MetadataResolver;
import io.binarycodes.harbor.library.ui.component.LibraryContent;

/**
 * Everything the reader has saved.
 */
@Route(value = "", layout = MainLayout.class)
public class LibraryView extends LibraryContent {

    public LibraryView(BookmarkService bookmarkService, LibraryFilter libraryFilter,
            MetadataResolver metadataResolver) {
        super(LibraryScope.ALL, bookmarkService, libraryFilter, metadataResolver);
    }
}
