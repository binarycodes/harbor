package io.binarycodes.harbor.library.service;

/**
 * Works out what a URL points at. Implementations may reach the network, so this
 * is never called on the UI thread.
 */
public interface MetadataResolver {

    LinkMetadata resolve(String url);
}
