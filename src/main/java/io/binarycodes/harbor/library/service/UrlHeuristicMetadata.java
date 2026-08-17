package io.binarycodes.harbor.library.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import io.binarycodes.harbor.library.domain.BookmarkType;

/**
 * What can be told about a link from the URL alone, for when the page itself
 * cannot be reached — it is offline, behind a login, or refusing us. The host
 * decides the kind of thing it is and a starting set of tags; the last meaningful
 * path segment stands in for a title.
 *
 * <p>Nothing is invented beyond that. There is deliberately no made-up
 * description: an empty field the reader can fill is more honest than a sentence
 * that only describes the fact that something was saved.
 */
public final class UrlHeuristicMetadata implements MetadataResolver {

    /**
     * A path segment has to look like prose to stand in for a title. One word of
     * six letters or more qualifies, and so does anything that was hyphenated into
     * several words — but short stubs like {@code /abs/} or {@code /p/} are route
     * furniture, and the site's own name reads better than they do.
     */
    private static final int SHORTEST_TITLE_WORD = 6;
    private static final Pattern FILE_EXTENSION = Pattern.compile("\\.(html?|php|aspx?)$");
    private static final Pattern SEPARATORS = Pattern.compile("[-_]+");
    private static final Pattern GENERIC_DOMAIN_PART =
            Pattern.compile("^(www|com|org|net|io|co|edu|gov)$");

    private static final List<HostRule> HOST_RULES = List.of(
            new HostRule(Pattern.compile("arxiv\\.org"), BookmarkType.PAPER, List.of("Research", "AI")),
            new HostRule(Pattern.compile("github\\.com"), BookmarkType.REPOSITORY, List.of("Tools", "Web")),
            new HostRule(Pattern.compile("youtube\\.com|youtu\\.be"), BookmarkType.VIDEO, List.of("Video")),
            new HostRule(Pattern.compile("nature\\.com|science\\.org|pubmed"), BookmarkType.PAPER,
                    List.of("Science", "Research")),
            new HostRule(Pattern.compile("medium\\.com|substack\\.com|blog"), BookmarkType.ARTICLE,
                    List.of("Reading")),
            new HostRule(Pattern.compile("wikipedia\\.org"), BookmarkType.GUIDE, List.of("Reference")));

    @Override
    public LinkMetadata resolve(String url) {
        URI parsed = parse(url);
        String host = host(parsed);
        HostRule rule = HOST_RULES.stream()
                .filter(candidate -> candidate.host().matcher(host).find())
                .findFirst()
                .orElse(new HostRule(null, BookmarkType.ARTICLE, List.of("Reading")));

        return new LinkMetadata(host, title(parsed, host), "", rule.tags(), rule.type(), 0, "");
    }

    static String host(URI uri) {
        if (uri == null || uri.getHost() == null) {
            return "link";
        }
        return uri.getHost().replaceFirst("^www\\.", "");
    }

    /**
     * The last path segment that reads like a title rather than an identifier,
     * falling back to the site's own name.
     */
    static String title(URI uri, String host) {
        String path = uri == null || uri.getPath() == null ? "" : uri.getPath();
        List<String> segments = Arrays.stream(path.split("/"))
                .filter(segment -> !segment.isBlank())
                .map(segment -> FILE_EXTENSION.matcher(segment).replaceAll(""))
                .map(segment -> SEPARATORS.matcher(segment).replaceAll(" ").strip())
                .toList();

        for (int index = segments.size() - 1; index >= 0; index--) {
            String segment = segments.get(index);
            boolean severalWords = segment.contains(" ");
            boolean longEnough = segment.replaceAll("[^a-zA-Z]", "").length() >= SHORTEST_TITLE_WORD;
            if (severalWords || longEnough) {
                return titleCase(segment);
            }
        }
        return titleCase(siteName(host));
    }

    private static String siteName(String host) {
        return Arrays.stream(host.split("\\."))
                .filter(part -> !GENERIC_DOMAIN_PART.matcher(part).matches())
                .findFirst()
                .orElse(host);
    }

    private static String titleCase(String text) {
        return Arrays.stream(text.split(" "))
                .filter(word -> !word.isBlank())
                .map(word -> word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1))
                .reduce((left, right) -> left + " " + right)
                .orElse(text);
    }

    private static URI parse(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            String candidate = url.strip();
            return new URI(candidate.matches("(?i)^https?://.*") ? candidate : "https://" + candidate);
        } catch (URISyntaxException notAUrl) {
            return null;
        }
    }

    private record HostRule(Pattern host, BookmarkType type, List<String> tags) {
    }
}
