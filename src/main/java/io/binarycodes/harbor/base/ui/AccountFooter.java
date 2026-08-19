package io.binarycodes.harbor.base.ui;

import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.security.AuthenticationContext;

/**
 * Who is signed in, and the way out. Signing out has to be reachable from every
 * screen: a reader on a shared machine who cannot find it has no way to stop being
 * signed in short of closing the browser.
 *
 * <p>Takes {@link AuthenticationContext} rather than a presenter of its own. It is
 * already the session-scoped API for exactly this question and its one answer, so a
 * Harbor presenter around it would carry no logic — see {@code CODING_CONVENTIONS}
 * §9, which this is a deliberate exception to.
 */
public class AccountFooter extends HorizontalLayout {

    public AccountFooter(AuthenticationContext authenticationContext) {
        addClassName("account-footer");
        setPadding(false);
        setAlignItems(Alignment.CENTER);
        setWidthFull();

        Span heading = new Span(getTranslation("account.signed_in"));
        heading.addClassName("account-footer-heading");

        Span name = new Span(readerName(authenticationContext));
        name.addClassName("account-footer-name");

        VerticalLayout reader = new VerticalLayout(heading, name);
        reader.addClassName("account-footer-reader");
        reader.setPadding(false);
        reader.setSpacing(false);

        add(reader, signOutButton(authenticationContext));
        setFlexGrow(1, reader);
    }

    /**
     * Logging out through the {@link AuthenticationContext} invalidates the Vaadin
     * session along with the security one, which is what disposes the session-scoped
     * presenters. Leaving them alive would hand the next reader on this browser the
     * previous one's cached library.
     */
    private Button signOutButton(AuthenticationContext authenticationContext) {
        Button signOut = new Button(VaadinIcon.SIGN_OUT.create(),
                event -> authenticationContext.logout());
        signOut.addThemeVariants(ButtonVariant.TERTIARY);
        signOut.addClassName("account-footer-sign-out");
        signOut.setAriaLabel(getTranslation("account.sign_out"));
        signOut.setTooltipText(getTranslation("account.sign_out"));
        return signOut;
    }

    /**
     * The name the realm knows, falling back to the subject. A reader whose realm
     * carries no profile still has to be told which account this is.
     */
    private static String readerName(AuthenticationContext authenticationContext) {
        return authenticationContext.getAuthenticatedUser(OidcUser.class)
                .map(reader -> reader.getPreferredUsername() != null
                        ? reader.getPreferredUsername()
                        : reader.getSubject())
                .orElseGet(() -> authenticationContext.getPrincipalName().orElse(""));
    }
}
