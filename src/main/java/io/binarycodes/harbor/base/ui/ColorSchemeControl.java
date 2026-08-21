package io.binarycodes.harbor.base.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.page.ColorScheme;

import io.binarycodes.harbor.library.domain.ColorSchemePreference;
import io.binarycodes.harbor.library.ui.presenter.LibraryPresenter;

/**
 * The light/dark control, as one item of the account menu.
 *
 * <p>Cycles through three states rather than flipping between two. Until the reader
 * picks a side the operating system decides, and a two-way toggle starting from there
 * appears to do nothing for anyone whose system is already set to the side the first
 * click would choose. The icon shows where the reader is now and the label names where
 * the next click goes, which is what makes a third state legible at all.
 *
 * <p>Storing the preference notifies the presenter's listeners, and the shell refreshes
 * the sidebar from there — so a click redraws this item without it having to say so.
 */
public class ColorSchemeControl extends Button {

    private final LibraryPresenter presenter;

    private ColorScheme.Value appliedColorScheme;

    public ColorSchemeControl(LibraryPresenter presenter) {
        this.presenter = presenter;

        addThemeVariants(ButtonVariant.TERTIARY);
        addClassName("account-menu-item");
        addClassName("color-scheme-control");
        addClickListener(event -> cycleColorScheme());
    }

    public void refresh() {
        ColorSchemePreference preference = presenter.getColorScheme();
        String label = getTranslation(labelKeyFor(next(preference)));
        setIcon(iconFor(preference));
        setText(label);
        setAriaLabel(label);
        applyColorScheme(valueFor(preference));
    }

    private void cycleColorScheme() {
        presenter.setColorScheme(next(presenter.getColorScheme()));
    }

    private void applyColorScheme(ColorScheme.Value value) {
        if (value == appliedColorScheme) {
            return;
        }
        appliedColorScheme = value;
        getUI().ifPresent(ui -> ui.getPage().setColorScheme(value));
    }

    private static ColorSchemePreference next(ColorSchemePreference preference) {
        return switch (preference) {
            case SYSTEM -> ColorSchemePreference.LIGHT;
            case LIGHT -> ColorSchemePreference.DARK;
            case DARK -> ColorSchemePreference.SYSTEM;
        };
    }

    private static ColorScheme.Value valueFor(ColorSchemePreference preference) {
        return switch (preference) {
            case SYSTEM -> ColorScheme.Value.LIGHT_DARK;
            case LIGHT -> ColorScheme.Value.LIGHT;
            case DARK -> ColorScheme.Value.DARK;
        };
    }

    private static Icon iconFor(ColorSchemePreference preference) {
        return switch (preference) {
            case SYSTEM -> VaadinIcon.ADJUST.create();
            case LIGHT -> VaadinIcon.SUN_O.create();
            case DARK -> VaadinIcon.MOON_O.create();
        };
    }

    private static String labelKeyFor(ColorSchemePreference preference) {
        return switch (preference) {
            case SYSTEM -> "colorscheme.switch.system";
            case LIGHT -> "colorscheme.switch.light";
            case DARK -> "colorscheme.switch.dark";
        };
    }
}
