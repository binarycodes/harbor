package io.binarycodes.harbor.library.ui.component;

import java.io.ByteArrayInputStream;
import java.util.Locale;
import java.util.function.Supplier;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;

/**
 * The way to the archived PDF. An anchor over Vaadin's own download handling rather
 * than a route of its own: the bytes then travel inside the session, so when
 * accounts arrive the archive is already scoped to whoever is signed in instead of
 * sitting behind a URL anyone could hold.
 */
public class ArchiveDownload extends Anchor {

    private static final int LONGEST_FILENAME_STEM = 60;

    public ArchiveDownload() {
        addClassName("reader-archive");
        setVisible(false);
        add(VaadinIcon.DOWNLOAD.create(), new Span(getTranslation("reader.archive.download")));
    }

    /**
     * @param title    what the file should be called, before it is made safe for a
     *                 filesystem
     * @param archive  asked for the bytes only once the reader clicks, so opening an
     *                 article never reads a PDF nobody wanted
     */
    public void show(String title, Supplier<byte[]> archive) {
        String filename = filenameFor(title);
        setHref(DownloadHandler.fromInputStream(request -> {
            byte[] bytes = archive.get();
            return new DownloadResponse(
                    new ByteArrayInputStream(bytes), filename, "application/pdf", bytes.length);
        }));
        setVisible(true);
    }

    public void hide() {
        setVisible(false);
    }

    /**
     * A title is prose and a filename is not: anything a filesystem or a
     * Content-Disposition header would object to becomes a hyphen.
     */
    static String filenameFor(String title) {
        String stem = title == null ? "" : title.strip().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (stem.isEmpty()) {
            stem = "archive";
        }
        if (stem.length() > LONGEST_FILENAME_STEM) {
            stem = stem.substring(0, LONGEST_FILENAME_STEM).replaceAll("-+$", "");
        }
        return stem + ".pdf";
    }
}
