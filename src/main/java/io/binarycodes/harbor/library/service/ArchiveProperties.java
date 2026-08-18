package io.binarycodes.harbor.library.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * What the archive is allowed to cost. Rendering a page pulls its images, so a
 * save turns into as many outbound requests as the page has figures — bounded
 * here, because the page decides how many that is and the reader is waiting.
 *
 * @param maxImages       how many images to fetch before rendering with what is in
 *                        hand
 * @param maxImageBytes   the largest single image worth embedding
 * @param maxTotalBytes   the budget across all of them, which a page of a few large
 *                        photographs would otherwise blow on its own
 */
@ConfigurationProperties("harbor.archive")
public record ArchiveProperties(int maxImages, int maxImageBytes, int maxTotalBytes) {

    public ArchiveProperties {
        maxImages = maxImages <= 0 ? 25 : maxImages;
        maxImageBytes = maxImageBytes <= 0 ? 2 * 1024 * 1024 : maxImageBytes;
        maxTotalBytes = maxTotalBytes <= 0 ? 12 * 1024 * 1024 : maxTotalBytes;
    }
}
