package io.binarycodes.harbor.library.ui.view;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ContextConfiguration;
import org.vaadin.addons.dramafinder.AbstractBasePlaywrightIT;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.vaadin.addons.dramafinder.element.TextAreaElement;
import org.vaadin.addons.dramafinder.element.TextFieldElement;

import io.binarycodes.harbor.StubMetadataConfiguration;

/**
 * The journey a reader actually takes through Harbor, in a real browser: arrive at
 * an empty library, save a link, read it, annotate it, and find the annotations
 * again.
 *
 * <p>The metadata resolver is stubbed so the run does not depend on someone else's
 * web server, but the storage is the browser's own — persistence is part of what
 * these tests are here to prove.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = StubMetadataConfiguration.class)
@DisplayName("A reader using Harbor")
class HarborJourneyIT extends AbstractBasePlaywrightIT {

    @LocalServerPort
    private int port;

    @Override
    public String getUrl() {
        return "http://localhost:" + port;
    }

    @Test
    @DisplayName("is invited to save something on a first visit")
    void firstVisitIsEmpty() {
        assertThat(page.getByText("Nothing here yet")).isVisible();
        assertThat(page.getByText("Save your first link to start building your library.")).isVisible();
    }

    @Test
    @DisplayName("saves a link and lands in the reader")
    void savesALinkAndReadsIt() {
        saveALink();

        assertThat(page.getByText(StubMetadataConfiguration.RESOLVED_TITLE).first()).isVisible();
        assertThat(page.getByText("Open original")).isVisible();
        assertThat(page.getByText(StubMetadataConfiguration.RESOLVED_PASSAGE)).isVisible();
    }

    @Test
    @DisplayName("finds the saved link in the library afterwards")
    void findsTheSavedLinkInTheLibrary() {
        saveALink();

        backToLibrary();

        assertThat(page.locator("vaadin-card.bookmark-card")).hasCount(1);
        assertThat(page.locator(".library-subtitle")).hasText("1 item");
    }

    @Test
    @DisplayName("writes a note and sees it rendered")
    void writesANote() {
        saveALink();

        new TextAreaElement(page.locator("vaadin-text-area.notes-editor-input"))
                .setValue("## Remember\n- the first point");
        // Notes save lazily, once typing pauses; give that pause time to happen.
        page.waitForTimeout(800);
        waitForVaadin();
        click(page.locator(".notes-editor-mode").last());
        waitForVaadin();

        assertThat(page.locator(".notes-editor-preview").getByText("Remember")).isVisible();
        assertThat(page.locator(".notes-editor-preview").getByText("the first point")).isVisible();
    }

    @Test
    @DisplayName("keeps a passage from the article and finds it on the highlights screen")
    void keepsAHighlight() {
        saveALink();

        selectThePassage();
        click(page.locator(".selection-highlight-button"));
        waitForVaadin();

        assertThat(page.locator("mark.reader-mark")).hasCount(1);
        assertThat(page.locator(".highlight-list-item")).hasCount(1);

        backToLibrary();
        click(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Highlights")));
        waitForVaadin();

        assertThat(page.locator(".highlight-group")).hasCount(1);
        assertThat(page.getByText("1 highlight")).isVisible();
    }

    @Test
    @DisplayName("queues an article for later and finds it in the queue")
    void queuesForLater() {
        saveALink();

        click(page.locator(".reader-read-later"));
        waitForVaadin();
        assertThat(page.getByText("In read later")).isVisible();

        backToLibrary();
        click(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Read later")));
        waitForVaadin();

        assertThat(page.locator("vaadin-card.bookmark-card")).hasCount(1);
    }

    @Test
    @DisplayName("can pack the library into a table instead of cards")
    void switchesListingDensity() {
        saveALink();
        backToLibrary();

        click(page.locator(".library-view-mode-button").last());
        waitForVaadin();

        assertThat(page.locator("vaadin-grid.bookmark-compact-grid")).isVisible();
    }

    /**
     * The reader's way back. Located by class rather than by its label, because
     * "Library" as text also matches the sidebar's "Research library" tagline.
     */
    private void backToLibrary() {
        click(page.locator(".reader-back"));
        waitForVaadin();
    }

    private void saveALink() {
        click(page.getByText("Save a link"));
        waitForVaadin();
        TextFieldElement.getByLabel(page, "URL").setValue("https://example.com/some-article");
        waitForVaadin();
        click(page.getByText("Fetch"));
        waitForVaadin();
        click(page.getByText("Save to library"));
        waitForVaadin();
    }

    /**
     * Selects the article's paragraph the way a reader would, by dragging across it,
     * so the browser produces a real selection for the highlight button to read.
     */
    private void selectThePassage() {
        Locator passage = page.getByText(StubMetadataConfiguration.RESOLVED_PASSAGE);
        passage.click(new Locator.ClickOptions().setClickCount(3));
        waitForVaadin();
    }

    private void waitForVaadin() {
        page.waitForFunction(WAIT_FOR_VAADIN_SCRIPT);
    }
}
