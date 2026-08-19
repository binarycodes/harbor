package io.binarycodes.harbor.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.Principal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.vaadin.flow.server.auth.AccessAnnotationChecker;

import io.binarycodes.harbor.base.ui.MainLayout;
import io.binarycodes.harbor.library.ui.view.HighlightsView;
import io.binarycodes.harbor.library.ui.view.LibraryView;
import io.binarycodes.harbor.library.ui.view.ReadLaterView;
import io.binarycodes.harbor.library.ui.view.ReaderView;

/**
 * That every screen is reachable by a signed-in reader and by nobody else — asserted
 * against the annotations themselves, with the same checker navigation access control
 * uses, so it needs no Spring context and no containers.
 *
 * <p>{@link MainLayout} is in the list for the reason this test exists. A route names it
 * with {@code layout = MainLayout.class}, and the checker vets every parent layout before
 * it vets the route: an unannotated shell denied all four screens, and said so as a
 * {@code RouteNotFoundError} rather than as anything about access.
 *
 * <p>A new screen belongs in this list. It is the cheapest place to notice that its
 * annotation is missing, and the annotation is the whole authorization model.
 */
@DisplayName("Access to a screen")
class RouteAccessTest {

    private final AccessAnnotationChecker checker = new AccessAnnotationChecker();

    static Class<?>[] everyGuardedClass() {
        return new Class<?>[] { MainLayout.class, LibraryView.class, ReadLaterView.class,
                HighlightsView.class, ReaderView.class };
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyGuardedClass")
    @DisplayName("is granted to a signed-in reader")
    void isGrantedToAReader(Class<?> guarded) {
        Principal reader = () -> "reader";

        assertTrue(checker.hasAccess(guarded, reader, role -> false),
                guarded.getSimpleName() + " denies a signed-in reader; it needs @PermitAll");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyGuardedClass")
    @DisplayName("is refused to a visitor who has not signed in")
    void isRefusedToAnonymous(Class<?> guarded) {
        assertFalse(checker.hasAccess(guarded, null, role -> false),
                guarded.getSimpleName() + " admits an anonymous visitor");
    }
}
