package io.binarycodes.harbor.library.ui.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.textfield.TextField;

import io.binarycodes.harbor.BrowserlessStorageConfiguration;
import io.binarycodes.harbor.HarborDatabase;
import io.binarycodes.harbor.StubIdentityConfiguration;
import io.binarycodes.harbor.StubMetadataConfiguration;
import io.binarycodes.harbor.library.domain.LibraryQuery;
import io.binarycodes.harbor.library.domain.LibraryScope;
import io.binarycodes.harbor.library.ui.component.SaveLinkDialog;
import io.binarycodes.harbor.library.ui.presenter.LibraryPresenter;

/**
 * The save dialog with {@code harbor.archive.force-before-save} off. Its own class
 * because the mode is a property, and one test because that is all the dialog does
 * differently: an empty archive stops being a refusal.
 *
 * <p>The mirror of {@code LibraryViewTest.requiresAnArchiveBeforeSaving}, which asserts
 * the same page is refused under the shipped default. The stub resolver's
 * {@code unarchivable} URL stands in for what every fetch looks like in this mode —
 * a page that was read, with no archive alongside it.
 */
@SpringBootTest(properties = "harbor.archive.force-before-save=false")
@ContextConfiguration(classes = { StubMetadataConfiguration.class, BrowserlessStorageConfiguration.class,
        StubIdentityConfiguration.class, HarborDatabase.class })
@DisplayName("Saving without waiting for the archive")
@ActiveProfiles("test")
class SaveWithoutWaitingTest extends SpringBrowserlessTest {

    @Autowired
    private ApplicationContext applicationContext;

    private LibraryPresenter presenter;

    @BeforeEach
    void startFromAnEmptyLibrary() {
        StubIdentityConfiguration.authenticate(StubIdentityConfiguration.READER);
        presenter = applicationContext.getBean(LibraryPresenter.class);
        presenter.load();
        presenter.find(LibraryQuery.of(LibraryScope.ALL))
                .forEach(bookmark -> presenter.remove(bookmark.id()));
    }

    @AfterEach
    void signOut() {
        StubIdentityConfiguration.forget();
    }

    @Test
    @DisplayName("a page with no archive yet is saveable rather than refused")
    void allowsALinkWhoseArchiveHasNotArrived() throws InterruptedException {
        navigate(LibraryView.class);
        find(Button.class).withCaption("Save a link").single().click();
        SaveLinkDialog dialog = find(SaveLinkDialog.class).single();
        find(TextField.class, dialog).withLabel("URL").single()
                .setValue("https://example.com/" + StubMetadataConfiguration.UNARCHIVABLE);

        find(Button.class, dialog).withCaption("Fetch").single().click();
        awaitSubmitEnabled(dialog);

        assertTrue(submitButton(dialog).isEnabled());
        assertFalse(urlField(dialog).isInvalid());

        submitButton(dialog).click();

        assertEquals(1, presenter.count());
    }

    private TextField urlField(SaveLinkDialog dialog) {
        return find(TextField.class, dialog).withLabel("URL").single();
    }

    private Button submitButton(SaveLinkDialog dialog) {
        return find(Button.class, dialog).withCaption("Save to library").single();
    }

    /**
     * The fetch hands its result back through {@code ui.access}, so the button has to
     * be looked up again each round rather than held on to.
     */
    private void awaitSubmitEnabled(SaveLinkDialog dialog) throws InterruptedException {
        for (int attempt = 0; attempt < 200 && !submitButton(dialog).isEnabled(); attempt++) {
            Thread.sleep(10);
            roundTrip();
        }
    }
}
