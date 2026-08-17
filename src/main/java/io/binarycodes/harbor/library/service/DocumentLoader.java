package io.binarycodes.harbor.library.service;

import java.io.IOException;

import org.jsoup.nodes.Document;

/**
 * Fetches a page and hands back the parsed document. The seam that keeps the network
 * out of the tests, and the one place an outbound request originates.
 */
interface DocumentLoader {

    Document load(String url) throws IOException;
}
