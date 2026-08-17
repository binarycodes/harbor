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

    @Bean
    @Primary
    public MetadataResolver stubMetadataResolver() {
        return url -> new LinkMetadata(RESOLVED_SITE, RESOLVED_TITLE, RESOLVED_DESCRIPTION, RESOLVED_TAGS,
                BookmarkType.ARTICLE, 7, "## A heading\n\n" + RESOLVED_PASSAGE, true);
    }
}
