package io.binarycodes.harbor.library.service;

/**
 * One row of the tag tally. An interface rather than a record because the query
 * that fills it is native, and Spring Data builds these by name from the result
 * set.
 */
interface TagCountRow {

    String getName();

    int getCount();
}
