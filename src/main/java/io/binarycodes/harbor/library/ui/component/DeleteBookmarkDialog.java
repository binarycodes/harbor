package io.binarycodes.harbor.library.ui.component;

import com.vaadin.flow.component.confirmdialog.ConfirmDialog;

/**
 * Asks before a bookmark goes. Deleting takes the notes and highlights with it and
 * the library is the only copy, so this is the one action in Harbor that cannot be
 * walked back — worth a question, and worth naming what is about to be lost.
 */
public class DeleteBookmarkDialog extends ConfirmDialog {

    public DeleteBookmarkDialog(String title, Runnable onConfirmed) {
        setHeader(getTranslation("bookmark.delete.title"));
        setText(getTranslation("bookmark.delete.text", title));
        setCancelable(true);
        setCancelText(getTranslation("bookmark.delete.cancel"));
        setConfirmText(getTranslation("bookmark.delete.confirm"));
        setConfirmButtonTheme("error primary");
        addConfirmListener(event -> onConfirmed.run());
    }
}
