package io.binarycodes.harbor.library.ui.view;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    @DisplayName("cannot save a link until its page has been read")
    void cannotSaveWithoutFetching() {
        click(page.getByText("Save a link"));
        waitForVaadin();
        TextFieldElement.getByLabel(page, "URL").setValue("https://example.com/some-article");
        waitForVaadin();

        Locator submit = page.locator("vaadin-button.save-link-submit");
        assertThat(submit).hasAttribute("disabled", "");

        click(page.getByText("Fetch"));
        waitForVaadin();

        assertThat(submit).not().hasAttribute("disabled", "");
    }

    @Test
    @DisplayName("is told when a link is already in the library")
    void refusesADuplicate() {
        saveALink();
        backToLibrary();

        click(page.getByText("Save a link"));
        waitForVaadin();
        TextFieldElement.getByLabel(page, "URL").setValue("https://example.com/some-article");
        waitForVaadin();
        click(page.getByText("Fetch"));
        waitForVaadin();
        click(page.getByText("Save to library"));
        waitForVaadin();

        assertThat(page.getByText("is already in your library", new Page.GetByTextOptions()
                .setExact(false))).isVisible();
        assertThat(page.locator("vaadin-card.bookmark-card")).hasCount(1);
    }

    @Test
    @DisplayName("corrects a bookmark's title from the library")
    void editsABookmark() {
        saveALink();
        backToLibrary();

        click(page.locator("vaadin-card.bookmark-card .edit-bookmark-button").first());
        waitForVaadin();
        // Filled through the input and then left, because the title field commits on
        // change rather than on every keystroke the way the URL field does.
        page.locator("vaadin-text-field.save-link-title input").fill("A Better Title");
        page.keyboard().press("Tab");
        waitForVaadin();
        click(page.getByText("Save changes"));
        waitForVaadin();

        assertThat(page.locator(".bookmark-card-title")).hasText("A Better Title");
        assertThat(page.locator("vaadin-card.bookmark-card")).hasCount(1);
    }

    @Test
    @DisplayName("deletes a bookmark only after confirming")
    void deletesABookmark() {
        saveALink();
        backToLibrary();

        click(page.locator("vaadin-card.bookmark-card .delete-bookmark-button").first());
        waitForVaadin();
        assertThat(page.getByText("Delete this bookmark?")).isVisible();

        click(page.getByText("Keep it"));
        waitForVaadin();
        assertThat(page.locator("vaadin-card.bookmark-card")).hasCount(1);

        click(page.locator("vaadin-card.bookmark-card .delete-bookmark-button").first());
        waitForVaadin();
        click(page.locator("vaadin-button[slot='confirm-button']"));
        waitForVaadin();

        assertThat(page.getByText("Nothing here yet")).isVisible();
    }

    @Test
    @DisplayName("orders the library by how long each read is, both ways round")
    void sortsByReadingTimeBothWays() {
        saveALink("https://example.com/a-long-article");
        backToLibrary();
        saveALink("https://example.com/a-short-note");
        backToLibrary();

        chooseSort("Longest read");
        assertThat(page.locator(".bookmark-card-title").first()).hasText("The Long Read");

        chooseSort("Shortest read");
        assertThat(page.locator(".bookmark-card-title").first()).hasText("The Short Read");
    }

    /**
     * The spacing complaint that started this: as separate children of the card's
     * footer the two controls inherited the gap meant to hold them apart from the
     * metadata. Grouped, they sit next to each other, so the distance between them
     * stays small however the footer is spaced.
     */
    @Test
    @DisplayName("keeps a card's edit and delete controls next to each other")
    void groupsTheCardActions() {
        saveALink();
        backToLibrary();

        Locator edit = page.locator("vaadin-card.bookmark-card .edit-bookmark-button").first();
        Locator delete = page.locator("vaadin-card.bookmark-card .delete-bookmark-button").first();

        double gap = delete.boundingBox().x - (edit.boundingBox().x + edit.boundingBox().width);

        assertTrue(gap < 8, "edit and delete should sit together, but were " + gap + "px apart");
    }

    private void chooseSort(String option) {
        click(page.locator(".library-sort-trigger"));
        waitForVaadin();
        click(page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(option)));
        waitForVaadin();
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
        saveALink("https://example.com/some-article");
    }

    private void saveALink(String url) {
        click(page.getByText("Save a link"));
        waitForVaadin();
        TextFieldElement.getByLabel(page, "URL").setValue(url);
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
