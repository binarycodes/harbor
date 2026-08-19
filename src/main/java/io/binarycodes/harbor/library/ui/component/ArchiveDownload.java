package io.binarycodes.harbor.library.ui.component;

import java.util.Locale;
import java.util.function.Supplier;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.AnchorTarget;
import com.vaadin.flow.component.html.AttachmentType;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.server.streams.DownloadHandler;

/**
 * The way to the archived PDF. An anchor over Vaadin's own download handling rather
 * than a route of its own: the bytes travel inside the session, so the archive is
 * scoped to whoever is signed in rather than sitting behind a URL anyone could hold.
 *
 * <p>It opens in a new tab rather than saving to disk. An archive is something to
 * read, and every browser has a PDF viewer — a file landing in Downloads asks the
 * reader to go and find it. Both halves are needed for that: the response says
 * {@code inline}, and {@link AttachmentType#INLINE} stops the anchor carrying a
 * {@code download} attribute, which would override it.
 *
 * <p>The disposition still names the file. Without it the viewer's tab, and any
 * copy the reader saves from it, is titled with the opaque id from the resource
 * URL.
 */
public class ArchiveDownload extends Anchor {

    private static final int LONGEST_FILENAME_STEM = 60;

    public ArchiveDownload() {
        addClassName("reader-archive");
        setVisible(false);
        // A new tab, so the reader keeps their place in the article they came from —
        // and handled by the browser rather than Flow's router, which is otherwise
        // attached to every anchor.
        setTarget(AnchorTarget.BLANK);
        setRouterIgnore(true);
        getElement().setAttribute("rel", "noopener");
        add(VaadinIcon.FILE_TEXT_O.create(), new Span(getTranslation("reader.archive.view")));
    }

    /**
     * @param title    what the file should be called if the reader goes on to save it
     *                 from their viewer, before it is made safe for a filesystem
     * @param archive  asked for the bytes only once the reader clicks, so opening an
     *                 article never reads a PDF nobody wanted
     */
    public void show(String title, Supplier<byte[]> archive) {
        String filename = filenameFor(title);
        setHref((DownloadHandler) event -> {
            byte[] bytes = archive.get();
            event.setContentType("application/pdf");
            event.setContentLength(bytes.length);
            event.inline(filename);
            event.getOutputStream().write(bytes);
        }, AttachmentType.INLINE);
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
