package io.binarycodes.harbor.base.ui;

import java.util.function.Consumer;

import org.springframework.stereotype.Component;

import com.vaadin.flow.component.page.WebStorage;

/**
 * The real browser's local storage.
 */
@Component
public class WebBrowserStorage implements BrowserStorage {

    @Override
    public void read(String key, Consumer<String> valueConsumer) {
        WebStorage.getItem(key, valueConsumer::accept);
    }

    @Override
    public void write(String key, String value) {
        WebStorage.setItem(key, value);
    }

    @Override
    public void remove(String key) {
        WebStorage.removeItem(key);
    }
}
