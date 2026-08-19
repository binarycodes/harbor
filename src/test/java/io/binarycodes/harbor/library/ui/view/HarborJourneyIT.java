package io.binarycodes.harbor.library.ui.view;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.vaadin.addons.dramafinder.AbstractBasePlaywrightIT;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import org.vaadin.addons.dramafinder.element.TextAreaElement;
import org.vaadin.addons.dramafinder.element.TextFieldElement;

import io.binarycodes.harbor.HarborDatabase;
import io.binarycodes.harbor.HarborIdentity;
import io.binarycodes.harbor.StubIdentityConfiguration;
import io.binarycodes.harbor.StubMetadataConfiguration;
import io.binarycodes.harbor.library.domain.LibraryQuery;
import io.binarycodes.harbor.library.domain.LibraryScope;
import io.binarycodes.harbor.library.service.BookmarkService;

/**
 * The journey a reader actually takes through Harbor, in a real browser: arrive at
 * an empty library, save a link, read it, annotate it, and find the annotations
 * again.
 *
 * <p>Signing in is part of arriving. This is the only tier with a browser, so it is
 * the only one that can drive Keycloak's own form — and therefore the only place the
 * redirect, the token exchange, the subject the library is owned by, and the logout
 * that has to reach Keycloak too are exercised against a real identity provider.
 *
 * <p>The metadata resolver is stubbed so the run does not depend on someone else's
 * web server, but the database and the identity provider are real: persistence and
 * ownership are part of what these tests are here to prove.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = { StubMetadataConfiguration.class, HarborDatabase.class })
@DisplayName("A reader using Harbor")
@ActiveProfiles("journey")
class HarborJourneyIT extends AbstractBasePlaywrightIT {

    /**
     * Long enough for a redirect to Keycloak, a form submission and a Vaadin page to
     * follow one another on a cold JVM; short enough that a broken arrival is reported
     * rather than waited out.
     */
    private static final double ARRIVAL_TIMEOUT_MILLIS = 20_000;

    @Autowired
    private BookmarkService bookmarkService;

    /**
     * A mapped port, which is right here and wrong in a deployment: under
     * {@code start-dev} Keycloak derives the issuer from the request host, and both this
     * JVM and Playwright's browser are on the host, so they agree.
     */
    @DynamicPropertySource
    static void pointAtTheContainerisedKeycloak(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.client.provider.keycloak.issuer-uri",
                HarborIdentity::issuerUri);
        // The client too, not just the issuer. application.properties defaults these to
        // the development realm's, and the client this fixture registered in its own
        // Keycloak is not that one — presenting the wrong secret fails as invalid_client.
        registry.add("spring.security.oauth2.client.registration.keycloak.client-id",
                () -> HarborIdentity.CLIENT_ID);
        registry.add("spring.security.oauth2.client.registration.keycloak.client-secret",
                () -> HarborIdentity.CLIENT_SECRET);
    }

    /**
     * The library used to be per-browser, so a fresh browser was a fresh library.
     * It is one database now, and these journeys share it — each has to start from
     * the empty library a first visit sees.
     *
     * <p>The cleanup runs on the test thread, which has been through no login, and the
     * owner refuses to guess. It is told which reader the journeys are about to sign in
     * as — HarborIdentity pins that user's id for exactly this.
     *
     * <p>The navigation matters: the base class opens the page before this runs, so
     * without it the browser is still showing whatever the previous journey left
     * behind. It now lands on Keycloak, so signing in is part of arriving.
     */
    @BeforeEach
    void startFromAnEmptyLibrary() {
        StubIdentityConfiguration.authenticate(HarborIdentity.SUBJECT);
        bookmarkService.find(LibraryQuery.of(LibraryScope.ALL))
                .forEach(bookmark -> bookmarkService.remove(bookmark.id()));
        page.navigate(getUrl());
        signIn();
        waitForVaadin();
    }

    @AfterEach
    void forgetTheReader() {
        StubIdentityConfiguration.forget();
    }

    @LocalServerPort
    private int port;

    @Override
    public String getUrl() {
        return "http://localhost:" + port;
    }

    @Test
    @DisplayName("arrives signed in, and is told which account they are in")
    void arrivesSignedIn() {
        assertThat(page.locator(".account-footer-name")).hasText(HarborIdentity.USERNAME);
    }

    /**
     * The failure this guards against looks exactly like a working button: without the
     * OIDC logout, Harbor's own session goes and Keycloak's stays, so the next visit
     * comes straight back through it and signs in again with nothing to click.
     */
    @Test
    @DisplayName("signs out, and has to ask again on the way back")
    void signsOutForReal() {
        click(page.locator(".account-footer-sign-out"));

        // Signing out lands back on Harbor, which needs a reader, which sends the
        // browser to Keycloak — so the form appearing is the round trip completing.
        assertThat(page.locator("#username")).isVisible();

        page.navigate(getUrl());

        assertThat(page.locator("#username")).isVisible();
    }

    /**
     * A deep link is the version of this that is easiest to get wrong: the route
     * resolves before the library is known, so an unauthenticated one has to be turned
     * away by the filter chain rather than by the screen.
     */
    @Test
    @DisplayName("cannot reach a screen without signing in")
    void deepLinksRequireSigningIn() {
        Page anonymous = getBrowser().newPage();
        try {
            anonymous.navigate(getUrl() + "/highlights");

            assertThat(anonymous.locator("#username")).isVisible();
        } finally {
            anonymous.close();
        }
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
     * Keycloak's form, filled the way a reader fills it. Submitted with Enter rather
     * than by clicking: the button's markup belongs to whichever login theme the version
     * ships, and the form's behaviour does not.
     *
     * <p>Every step says what it was looking at when it gave up. An earlier version
     * returned quietly when it could not find the form, on the theory that the session
     * had survived — which turned any single arrival problem into thirteen journeys
     * timing out on "Save a link" with nothing to say about why.
     */
    private void signIn() {
        Locator loginForm = page.locator("#username");
        awaitEither(loginForm, "neither Harbor nor a Keycloak login form");
        if (loginForm.count() == 0) {
            // Already signed in: the base class opened the page before this ran and the
            // session outlived the second navigation.
            return;
        }
        loginForm.fill(HarborIdentity.USERNAME);
        Locator password = page.locator("#password");
        password.fill(HarborIdentity.PASSWORD);
        password.press("Enter");
        awaitShell();
    }

    /**
     * Waits for Harbor's drawer or the given alternative, whichever arrives.
     */
    private void awaitEither(Locator alternative, String describeFailure) {
        try {
            harborShell().or(alternative).first()
                    .waitFor(new Locator.WaitForOptions().setTimeout(ARRIVAL_TIMEOUT_MILLIS));
        } catch (TimeoutError gaveUp) {
            throw new AssertionError(whereAmI("Arrived at " + describeFailure), gaveUp);
        }
    }

    /**
     * Harbor itself, rendered. Signing in successfully and then not landing here is the
     * interesting failure: it means the credentials were accepted and something after
     * that — navigation access control, most likely — refused the screen.
     */
    private void awaitShell() {
        try {
            harborShell().waitFor(new Locator.WaitForOptions().setTimeout(ARRIVAL_TIMEOUT_MILLIS));
        } catch (TimeoutError neverRendered) {
            throw new AssertionError(whereAmI("Submitted the login form as "
                    + HarborIdentity.USERNAME + ", but Harbor's drawer never rendered"),
                    neverRendered);
        }
    }

    /**
     * The drawer, which every screen is wrapped in and which no other page has.
     */
    private Locator harborShell() {
        return page.locator(".sidebar");
    }

    private String whereAmI(String what) {
        return what + ". The browser was at " + page.url() + ", showing \"" + page.title() + "\".";
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
