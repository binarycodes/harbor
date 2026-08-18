package io.binarycodes.harbor.library.service;

/**
 * One page's kept passages as the database returns them, without the article they
 * came from.
 */
interface HighlightGroupRow {

    String getId();

    String getTitle();

    String getSite();

    String getHighlights();
}
