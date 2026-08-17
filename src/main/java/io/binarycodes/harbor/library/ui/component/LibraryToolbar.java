package io.binarycodes.harbor.library.ui.component;

import java.util.EnumMap;
import java.util.Map;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;

import io.binarycodes.harbor.library.domain.SortMode;
import io.binarycodes.harbor.library.domain.ViewMode;
import io.binarycodes.harbor.library.service.LibraryFilter;

/**
 * The header of a library listing: what you are looking at, how many there are,
 * and the controls for narrowing, ordering, and packing it.
 */
public class LibraryToolbar extends HorizontalLayout {

    private final LibraryFilter libraryFilter;
    private final H1 heading = new H1();
    private final Span subtitle = new Span();
    private final TextField search = new TextField();
    private final Span sortLabel = new Span();
    private final Map<SortMode, MenuItem> sortItems = new EnumMap<>(SortMode.class);
    private final Map<ViewMode, Button> viewModeButtons = new EnumMap<>(ViewMode.class);

    public LibraryToolbar(LibraryFilter libraryFilter) {
        this.libraryFilter = libraryFilter;

        addClassName("library-toolbar");
        setWidthFull();
        setAlignItems(Alignment.CENTER);

        add(headingBlock(), searchField(), sortMenu(), viewModeSwitch());
    }

    public void setHeading(String title, String summary) {
        heading.setText(title);
        subtitle.setText(summary);
    }

    public void refresh() {
        if (!search.getValue().equals(libraryFilter.getSearchText())) {
            search.setValue(libraryFilter.getSearchText());
        }
        SortMode sortMode = libraryFilter.getSortMode();
        sortLabel.setText(getTranslation(sortMode.translationKey()));
        sortItems.forEach((mode, item) -> item.setChecked(mode == sortMode));
        viewModeButtons.forEach((mode, button) -> {
            boolean selected = mode == libraryFilter.getViewMode();
            button.setClassName("selected", selected);
            button.getElement().setAttribute("aria-pressed", String.valueOf(selected));
        });
    }

    private Div headingBlock() {
        heading.addClassName("library-heading");
        subtitle.addClassName("library-subtitle");
        Div block = new Div(heading, subtitle);
        block.addClassName("library-heading-block");
        return block;
    }

    private TextField searchField() {
        search.addClassName("library-search");
        search.setPlaceholder(getTranslation("library.search.placeholder"));
        search.setAriaLabel(getTranslation("library.search.placeholder"));
        search.setPrefixComponent(VaadinIcon.SEARCH.create());
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.addValueChangeListener(event -> libraryFilter.setSearchText(event.getValue()));
        return search;
    }

    private MenuBar sortMenu() {
        MenuBar menu = new MenuBar();
        menu.addClassName("library-sort");
        menu.addThemeVariants(MenuBarVariant.TERTIARY);

        Span trigger = new Span(VaadinIcon.SORT.create(),
                new Span(getTranslation("library.sort.prefix")), sortLabel);
        trigger.addClassName("library-sort-trigger");
        SubMenu options = menu.addItem(trigger).getSubMenu();

        for (SortMode mode : SortMode.values()) {
            MenuItem option = options.addItem(getTranslation(mode.translationKey()),
                    event -> libraryFilter.setSortMode(mode));
            option.setCheckable(true);
            sortItems.put(mode, option);
        }
        return menu;
    }

    private Div viewModeSwitch() {
        Div group = new Div();
        group.addClassName("library-view-mode");
        group.getElement().setAttribute("role", "group");
        group.getElement().setAttribute("aria-label", getTranslation("library.mode.label"));

        addViewModeButton(group, ViewMode.CARDS, VaadinIcon.GRID);
        addViewModeButton(group, ViewMode.ROWS, VaadinIcon.LIST);
        addViewModeButton(group, ViewMode.COMPACT, VaadinIcon.TABLE);
        return group;
    }

    private void addViewModeButton(Div group, ViewMode mode, VaadinIcon icon) {
        Button button = new Button(icon.create(), event -> libraryFilter.setViewMode(mode));
        button.addThemeVariants(ButtonVariant.TERTIARY);
        button.addClassName("library-view-mode-button");
        String label = getTranslation(mode.translationKey());
        button.setAriaLabel(label);
        button.setTooltipText(label);
        viewModeButtons.put(mode, button);
        group.add(button);
    }
}
