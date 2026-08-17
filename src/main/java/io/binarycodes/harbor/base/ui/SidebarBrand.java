package io.binarycodes.harbor.base.ui;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * The application's name and mark at the top of the drawer.
 */
public class SidebarBrand extends HorizontalLayout {

    public SidebarBrand() {
        addClassName("sidebar-brand");
        setAlignItems(Alignment.CENTER);
        setPadding(false);
        setSpacing(false);

        Div mark = new Div(VaadinIcon.BOOKMARK.create());
        mark.addClassName("sidebar-brand-mark");

        Span name = new Span(getTranslation("app.name"));
        name.addClassName("sidebar-brand-name");
        Span tagline = new Span(getTranslation("app.tagline"));
        tagline.addClassName("sidebar-brand-tagline");

        VerticalLayout wordmark = new VerticalLayout(name, tagline);
        wordmark.addClassName("sidebar-brand-wordmark");
        wordmark.setPadding(false);
        wordmark.setSpacing(false);

        add(mark, wordmark);
    }
}
