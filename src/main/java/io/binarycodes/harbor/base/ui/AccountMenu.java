package io.binarycodes.harbor.base.ui;

import java.util.stream.Stream;

import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.popover.PopoverPosition;
import com.vaadin.flow.spring.security.AuthenticationContext;

import io.binarycodes.harbor.library.ui.presenter.LibraryPresenter;

/**
 * What the avatar in the drawer's foot opens: who is signed in, the appearance control,
 * and the way out. Signing out has to be reachable from every screen — a reader on a
 * shared machine who cannot find it has no way to stop being signed in short of closing
 * the browser — and one click on their own avatar is where a reader looks for it.
 *
 * <p>Owns the avatar's label as well as its own contents, because both answer the same
 * question and asking the identity twice is how the two drift apart.
 *
 * <p>Takes {@link AuthenticationContext} rather than a presenter of its own. It is
 * already the session-scoped API for exactly this question and its one answer, so a
 * Harbor presenter around it would carry no logic — see {@code CODING_CONVENTIONS}
 * §9, which this is a deliberate exception to.
 */
public class AccountMenu extends Popover {

    private final ColorSchemeControl colorSchemeControl;

    public AccountMenu(Avatar avatar, LibraryPresenter presenter,
            AuthenticationContext authenticationContext) {
        colorSchemeControl = new ColorSchemeControl(presenter);

        String name = reader(authenticationContext);
        avatar.setName(name);
        avatar.addClassName("account-avatar");

        addClassName("account-menu");
        setTarget(avatar);
        // Upwards, because the footer is the bottom of the drawer and a menu opening
        // downwards has nowhere to go. It is wider than the drawer either way, so it
        // reaches over the view beside it as any menu does.
        setPosition(PopoverPosition.TOP_END);

        add(readerName(name), colorSchemeControl, signOutButton(authenticationContext));
    }

    public void refresh() {
        colorSchemeControl.refresh();
    }

    /**
     * The name on its own, with nothing labelling it. A reader looking at their own
     * avatar's menu knows whose account it is; being told "signed in as" first only
     * pushes the one thing they came to check down a line.
     */
    private static Span readerName(String name) {
        Span readerName = new Span(name);
        readerName.addClassName("account-menu-name");
        return readerName;
    }

    /**
     * Logging out through the {@link AuthenticationContext} invalidates the Vaadin
     * session along with the security one, which is what disposes the session-scoped
     * presenters. Leaving them alive would hand the next reader on this browser the
     * previous one's cached library.
     */
    private Button signOutButton(AuthenticationContext authenticationContext) {
        Button signOut = new Button(getTranslation("account.sign_out"),
                VaadinIcon.SIGN_OUT.create(), event -> authenticationContext.logout());
        signOut.addThemeVariants(ButtonVariant.TERTIARY);
        signOut.addClassName("account-menu-item");
        signOut.addClassName("account-menu-sign-out");
        return signOut;
    }

    /**
     * The reader's full name, falling back through the username to the subject. A realm
     * need not carry a profile, and a reader whose realm does not still has to be told
     * which account this is — an identifier they recognise beats a blank line.
     */
    private static String reader(AuthenticationContext authenticationContext) {
        return authenticationContext.getAuthenticatedUser(OidcUser.class)
                .map(reader -> firstPresent(reader.getFullName(), reader.getPreferredUsername(),
                        reader.getSubject()))
                .orElseGet(() -> authenticationContext.getPrincipalName().orElse(""));
    }

    private static String firstPresent(String... candidates) {
        return Stream.of(candidates)
                .filter(candidate -> candidate != null && !candidate.isBlank())
                .findFirst()
                .orElse("");
    }
}
