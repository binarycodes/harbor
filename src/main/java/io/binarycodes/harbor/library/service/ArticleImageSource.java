package io.binarycodes.harbor.library.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.jsoup.nodes.Element;

/**
 * Where an image actually lives. Reading {@code src} is not enough on most of the
 * web: publishers defer image loading, so {@code src} holds a placeholder — often
 * a one-pixel {@code data:} GIF — and the real URL sits in {@code srcset}, in a
 * {@code data-} attribute, or on a {@code <picture>} source.
 *
 * <p>Getting this wrong is quiet. The archive still renders, still validates as a
 * PDF, and simply has blank space where the figures should be.
 *
 * <p>Format matters as much as address: publishers put AVIF and WebP first because
 * browsers prefer them, and PDFBox can decode neither. A candidate in a format
 * that cannot be embedded is worse than no candidate, because it costs a fetch to
 * find out.
 */
final class ArticleImageSource {

    private static final List<String> LAZY_ATTRIBUTES =
            List.of("data-src", "data-original", "data-lazy-src", "data-actualsrc");
    private static final List<String> LAZY_SET_ATTRIBUTES = List.of("data-srcset", "srcset");

    /**
     * Formats PDFBox can embed. Anything else is skipped rather than fetched and
     * discarded.
     */
    private static final Pattern EMBEDDABLE =
            Pattern.compile("(?i)\\.(jpe?g|png|gif)(\\?|#|$)");

    private static final Pattern HTTP_SCHEME = Pattern.compile("(?i)^https?://");

    private ArticleImageSource() {
    }

    /**
     * @return an absolute http(s) URL in a format that can be embedded, or empty
     *         when this image offers none
     */
    static Optional<String> bestFor(Element image) {
        return fromPicture(image)
                .or(() -> fromSets(image))
                .or(() -> fromLazyAttributes(image))
                .or(() -> fromSrc(image));
    }

    /**
     * A {@code <picture>} lists its best format first, which is exactly the one
     * PDFBox is least likely to read — so the sources are filtered rather than
     * ranked.
     */
    private static Optional<String> fromPicture(Element image) {
        Element picture = image.closest("picture");
        if (picture == null) {
            return Optional.empty();
        }
        return picture.select("source").stream()
                .filter(source -> isEmbeddableType(source.attr("type")))
                .map(source -> widest(source.attr("srcset"), source))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static Optional<String> fromSets(Element image) {
        return LAZY_SET_ATTRIBUTES.stream()
                .map(attribute -> widest(image.attr(attribute), image))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static Optional<String> fromLazyAttributes(Element image) {
        return LAZY_ATTRIBUTES.stream()
                .map(attribute -> resolve(image.attr(attribute), image))
                .flatMap(Optional::stream)
                .findFirst();
    }

    /**
     * A {@code data:} URI needs no candidate at all — it is already the bytes, and
     * {@link #resolve} drops it for not being http. Whether those bytes are a real
     * picture or a spacer holding the layout open is the renderer's question, since
     * it is the one deciding what to keep.
     */
    private static Optional<String> fromSrc(Element image) {
        return resolve(image.attr("src"), image);
    }

    /**
     * The largest candidate a {@code srcset} offers. Width descriptors are the
     * common form; where they are absent the order the publisher wrote them in is
     * all there is to go on.
     */
    private static Optional<String> widest(String srcset, Element base) {
        if (srcset.isBlank()) {
            return Optional.empty();
        }
        record Candidate(String url, int width) {
        }
        List<Candidate> candidates = List.of(srcset.split(",")).stream()
                .map(String::strip)
                .filter(entry -> !entry.isEmpty())
                .map(entry -> {
                    String[] parts = entry.split("\\s+");
                    return new Candidate(parts[0], parts.length > 1 ? widthOf(parts[1]) : 0);
                })
                .toList();
        return candidates.stream()
                .max(Comparator.comparingInt(Candidate::width))
                .map(Candidate::url)
                .flatMap(url -> resolve(url, base));
    }

    private static int widthOf(String descriptor) {
        try {
            return descriptor.toLowerCase(Locale.ROOT).endsWith("w")
                    ? Integer.parseInt(descriptor.substring(0, descriptor.length() - 1))
                    : 0;
        } catch (NumberFormatException notAWidth) {
            return 0;
        }
    }

    /**
     * Relative URLs are resolved against the page they came from — which is the
     * final URL after redirects, since that is what the document's base URI holds.
     */
    private static Optional<String> resolve(String candidate, Element base) {
        if (candidate == null || candidate.isBlank()) {
            return Optional.empty();
        }
        String absolute = candidate;
        if (!HTTP_SCHEME.matcher(candidate).find()) {
            try {
                absolute = new URI(base.baseUri()).resolve(candidate.strip()).toString();
            } catch (URISyntaxException | IllegalArgumentException notResolvable) {
                return Optional.empty();
            }
        }
        if (!HTTP_SCHEME.matcher(absolute).find() || !EMBEDDABLE.matcher(absolute).find()) {
            return Optional.empty();
        }
        return Optional.of(absolute);
    }

    private static boolean isEmbeddableType(String type) {
        String declared = type.toLowerCase(Locale.ROOT);
        return declared.isBlank()
                || declared.equals("image/jpeg")
                || declared.equals("image/png")
                || declared.equals("image/gif");
    }
}
