package io.binarycodes.harbor.base.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.page.ColorScheme;

import io.binarycodes.harbor.library.domain.ColorSchemePreference;
import io.binarycodes.harbor.library.ui.presenter.LibraryPresenter;

/**
 * The foot of the drawer: a reminder that the library lives on this device only,
 * and the light/dark control.
 *
 * <p>The control cycles through three states rather than flipping between two.
 * Until the reader picks a side the operating system decides, and a two-way
 * toggle starting from there appears to do nothing for anyone whose system is
 * already set to the side the first click would choose.
 */
public class StorageFooter extends HorizontalLayout {

    private final LibraryPresenter presenter;
    private final Span itemCount = new Span();
    private final Button colorSchemeToggle = new Button();

    private ColorScheme.Value appliedColorScheme;

    public StorageFooter(LibraryPresenter presenter) {
        this.presenter = presenter;

        addClassName("storage-footer");
        setPadding(false);
        setAlignItems(Alignment.CENTER);
        setWidthFull();

        Span indicator = new Span();
        indicator.addClassName("storage-footer-indicator");

        Span heading = new Span(getTranslation("sidebar.storage"));
        heading.addClassName("storage-footer-heading");
        itemCount.addClassName("storage-footer-count");

        VerticalLayout status = new VerticalLayout(heading, itemCount);
        status.addClassName("storage-footer-status");
        status.setPadding(false);
        status.setSpacing(false);

        colorSchemeToggle.addThemeVariants(ButtonVariant.TERTIARY);
        colorSchemeToggle.addClassName("color-scheme-toggle");
        colorSchemeToggle.addClickListener(event -> cycleColorScheme());

        add(indicator, status, colorSchemeToggle);
        setFlexGrow(1, status);
    }

    public void refresh() {
        int count = presenter.count();
        itemCount.setText(count == 1
                ? getTranslation("sidebar.storage.items.one")
                : getTranslation("sidebar.storage.items.many", count));
        ColorSchemePreference preference = presenter.getColorScheme();
        colorSchemeToggle.setIcon(iconFor(preference));
        colorSchemeToggle.setAriaLabel(getTranslation(labelKeyFor(next(preference))));
        colorSchemeToggle.setTooltipText(getTranslation(labelKeyFor(next(preference))));
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
