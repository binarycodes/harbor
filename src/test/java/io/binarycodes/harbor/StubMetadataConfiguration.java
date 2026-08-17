package io.binarycodes.harbor;

import java.util.List;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import io.binarycodes.harbor.library.domain.BookmarkType;
import io.binarycodes.harbor.library.service.LinkMetadata;
import io.binarycodes.harbor.library.service.MetadataResolver;

/**
 * Keeps the tests off the network. The real resolver fetches whatever URL it is
 * given, which would make every test depend on a stranger's web server being up
 * and unchanged.
 */
@TestConfiguration
public class StubMetadataConfiguration {

    public static final String RESOLVED_SITE = "example.com";
    public static final String RESOLVED_TITLE = "A Page About Testing";
    public static final String RESOLVED_DESCRIPTION = "What the page says it is about.";
    public static final String RESOLVED_PASSAGE = "A paragraph worth highlighting in the reader.";
    public static final List<String> RESOLVED_TAGS = List.of("Reading");

    /**
     * A URL containing "long" or "short" comes back with a reading time to match, so a
     * test can put two saved links in a known order. Anything else gets
     * {@link #RESOLVED_MINUTES}.
     */
    public static final int RESOLVED_MINUTES = 7;
    public static final int LONG_READ_MINUTES = 30;
    public static final int SHORT_READ_MINUTES = 2;

    @Bean
    @Primary
    public MetadataResolver stubMetadataResolver() {
        return url -> new LinkMetadata(RESOLVED_SITE, titleFor(url), RESOLVED_DESCRIPTION, RESOLVED_TAGS,
                BookmarkType.ARTICLE, minutesFor(url), "## A heading\n\n" + RESOLVED_PASSAGE, true);
    }

    private static String titleFor(String url) {
        if (url.contains("long")) {
            return "The Long Read";
        }
        if (url.contains("short")) {
            return "The Short Read";
        }
        return RESOLVED_TITLE;
    }

    private static int minutesFor(String url) {
        if (url.contains("long")) {
            return LONG_READ_MINUTES;
        }
        if (url.contains("short")) {
            return SHORT_READ_MINUTES;
        }
        return RESOLVED_MINUTES;
    }
}
