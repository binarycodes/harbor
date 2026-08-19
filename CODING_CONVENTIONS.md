# Coding Conventions

Project-wide rules. Once a pattern is established here, follow it without prompting.

## 1. Naming

- Full, meaningful identifiers everywhere (variables, params, lambda captures, locals). No abbreviations beyond well-known ones (`irr` ok, `infl` not).
- Methods named for what they do, not how.
- Lambda parameters get real names; a genuinely unused one is `ignored` / `unused`.
- Constants replace magic numbers.

## 2. Comments

- Default to none — names, structure, and types carry intent.
- Comment only the non-obvious, and only the *why*.
- No section-divider banners. Class-level Javadoc for one-paragraph orientation is fine.
- Delete stale narrative and TODOs.

## 3. Code structure

- One concern per class. Extract the form, each chart (its own class), and the grid; the view stays thin — composition, state transitions, and the one method that ties them together.
- Components extend the closest Vaadin primitive (`Card`, `Grid<T>`, `Chart`, `VerticalLayout`) and expose a focused API (`setX` / `update` / `getInputs` / `addXChangeListener`). Stop extending it when its shadow layout fights the design: `Card` positions its own title/suffix slots, so a row that needs a trailing element pinned while the title truncates is a plain flex layout instead.
- `Card` implements neither `HasStyle` nor `HasTooltip`. Reach for `getElement().getClassList()`, and don't expect `addClassName` / `getStyle` on it. Same for `Markdown`.
- Vaadin `Button` takes an icon and a label and nothing else — `add()` is private. A control that needs three pieces of content (a color dot, a name, a count) is a `NativeButton` with `aria-pressed`.
- Helpers live with the thing they help.
- Data grids extend `Grid<T>` directly. Introduce a column-chooser wrapper only when a projection grid actually needs one.
- Prefer a named private method over a long inline block.

## 4. Java

- Records for immutable value types; mutable Lombok beans where a framework demands one — `Binder` inputs and JPA entities — with null-defaulted fields.
- Lombok `@Getter @Setter @NoArgsConstructor @AllArgsConstructor`, declared `<optional>true</optional>`.
- Jackson 3 — import from `tools.jackson.*`; build mappers via `JsonMapper.builder().build()`.
- Don't fabricate fallback defaults in form-read paths; surface empties through binder validation.
- `BigDecimal` for money — `MathContext.DECIMAL64` for arithmetic, `RoundingMode.HALF_UP` for display.
- `var` when the right-hand side makes the type obvious.
- Multi-line strings use text blocks with `.formatted(...)`, never `+`.

## 5. Vaadin / form binding

- Bind every field through `Binder<T>` with explicit `bind(getter, setter)`.
- Validators where they belong: `asRequired`, the range validators, `withValidator` for cross-field rules (re-trigger via the dependent field's value-change listener).
- `ValueChangeMode.LAZY` on number/text inputs. `CustomField` wrappers propagate inner-field changes with `updateValue()`.
- `binder.writeBeanAsDraft(target)` for possibly-invalid reads; pair with `isValid()` / `validate()`.
- Use semantic components: `RadioButtonGroup`, `Badge`, `Card` with the `status` attribute.
- Composite widgets extend `CustomField<T>` (single value) or a `Card` subclass.

## 6. CSS

- One file per concern; the entry stylesheet holds only `@import` lines.
- Colors are semantic tokens in `colors.css`, referenced via `var(--color-…)`. No raw hex / rgb / `color-mix` / named colors elsewhere.
- Light and dark are Aura's `color-scheme`, not a class on `html`. Own tokens adapt with the CSS `light-dark()` function; the scheme is set from Java with `Page::setColorScheme`. There is no `html.dark` selector.
- Prefer Vaadin / theme variables over hard-coded values: `--vaadin-*` base properties (`--vaadin-text-color`, `--vaadin-border-color`, `--vaadin-radius-m`, `--vaadin-gap-s`) and `--aura-*` for what only Aura defines (`--aura-accent-color`, `--aura-surface-color`, `--aura-font-size-s`, `--aura-shadow-xs`).
- Style Vaadin components through their own custom properties (`--vaadin-button-background`) rather than `background` on the host, which the component's base styles win against.
- `box-sizing: border-box` is set globally in `shell.css`. Padded full-width containers otherwise overflow their parent at phone widths.
- One-paragraph header comment per file; no section dividers.

## 7. Internationalization

- No user-facing string literals in code. Every label, title, hint, placeholder, tooltip, validation message, grid header, chart title/axis/series, notification, and aria-label is a key in `src/main/resources/vaadin-i18n/translations.properties`.
- Resolve via `getTranslation(key, args…)` on a `Component`. A non-component helper takes the calling `Component` and resolves through it rather than reaching for a static lookup.
- Reuse and parameterise shared keys; namespace them by feature (`library.*`, `reader.*`, `save.*`, `highlights.*`, `bookmark.*`).
- Counts that can be one carry both forms (`library.count.one` / `library.count.many`) and the code picks on the number — `MessageFormat` alone cannot.
- Enums own their key (`SortMode::translationKey`) so nothing switches on a label. Page titles come from `HasDynamicTitle`.
- Never `switch` on a translated string; switch on an enum or index.

## 8. Responsive layout

- Every form and result is usable from ~375px to wide desktop with no horizontal overflow.
- Field grids use `FormLayout` with responsive steps, collapsing to one column.
- Repeating rows add the `form-row` class; summary-card rows add `summary-row`.
- Containers fill width (`setWidthFull()`); no fixed pixel widths.
- Verify at mobile and desktop widths before declaring done.

## 9. Project layout

- Feature-based packaging: `base/` for shared infrastructure, feature packages with their own `domain/` / `service/` / `ui/`, plus `ui/presenter/` for the session-scoped classes the screens talk to.
- Dependencies run one way: `ui` → `ui/presenter` → `service` → storage, never backwards. A service imports no Vaadin: a change listener is how a component learns to redraw itself, so listeners and the orchestration around them belong to the presenter, which is also the only layer that needs to be session-scoped.
- One public class per file; helpers stay private static unless reused.

## 10. Persistence

- PostgreSQL through Spring Data JPA. Flyway owns the schema (`db/migration/V*.sql`) and `spring.jpa.hibernate.ddl-auto=validate` fails startup on drift rather than rewriting it.
- **Entities, projections and repositories are package-private and never leave the service package.** The service translates them to records before anything above sees them — which keeps the domain immutable, and keeps every entity inside the transaction that loaded it, so a stateful UI framework never meets a detached one. A view that tries to import an entity does not compile; that is the point.
- Mutable JPA entities take the same Lombok treatment as `Binder` beans (see §4).
- Every table carries `owner_id` and every query is scoped by it. Accounts arrived exactly that way — `LibraryOwner.current()` returns the authenticated OIDC subject and no migration or query changed. It refuses rather than defaulting when nobody is signed in: a fallback owner would put an unauthenticated path back to writing into one shared library.
- `byte[]`, never `@Lob`: on PostgreSQL, Hibernate maps `@Lob byte[]` to an `oid` large object rather than `bytea`.
- Browser storage (`BrowserStorage`) is only for what describes this browser rather than the library — the light/dark choice, and reading what an older version left behind.

## 10a. Security headers

- Spring Security writes its headers as the response commits, and the response Vaadin renders a page into never commits that way — so the application's own routes come out with no headers at all while static resources get the full set. `SecurityConfig` fixes this with an `ObjectPostProcessor<HeaderWriterFilter>` calling `setShouldWriteHeadersEagerly(true)`. Verify a header change by curling an application route (`/`, `/read/…`), never only a static file.
- Headers belong in `SecurityConfig`, not a parallel servlet filter — one source of truth, and Spring Security keeps the conditional ones (HSTS only over HTTPS) conditional.
- HSTS only goes out when the app believes the request was secure, and behind a TLS-terminating proxy it only ever sees plain HTTP. `FORWARD_HEADERS_STRATEGY=native` opts into trusting the proxy's `X-Forwarded-*` headers; it stays unset by default because those headers are client-supplied and spoofable with nothing in front.

## 10b. Access control

- Login is mandatory. `SecurityConfig` leaves `VaadinSecurityConfigurer` on its defaults and points `oauth2LoginPage` at the OIDC registration, so an unauthenticated request redirects to the identity provider rather than to a Harbor login view — Harbor collects no credentials of its own.
- **No product name appears in `src/main`.** The registration is called `oidc`, not after whatever the development stack happens to run, because Spring builds `/oauth2/authorization/oidc` and the `/login/oauth2/code/oidc` callback from that id — so a vendor named there would end up in Harbor's URLs and in every deployment's provider configuration. Harbor needs discovery, the authorization-code flow, an id token and RP-initiated logout, and nothing beyond them. Keycloak belongs to `environment/dev/` and to the test fixtures only.
- **A route with no access annotation is denied, and so is its parent layout's.** Navigation access control is on, so every `@Route` carries `@PermitAll` — and so does `MainLayout`. `AnnotatedViewAccessChecker` checks each parent layout a route names before it checks the route, and an unannotated layout denies everything behind it: the symptom is a `RouteNotFoundError` and a log line about the view allowing "broader access than the layout". This applies to a layout referenced as `layout = MainLayout.class`, not only to one annotated `@Layout`. "Authenticated" is the whole authorization model — there are no roles.
- A logout that only drops Harbor's session leaves the provider's intact and the next visit signs straight back in. `OidcClientInitiatedLogoutSuccessHandler` is what makes the button mean what it says.
- `AuthenticationContext` is the presenter-shaped API for who is signed in and for logging out, so a component may take it directly rather than through a Harbor presenter (`AccountFooter`) — the one sanctioned exception to §9, because a wrapper would hold no logic.
- **The issuer the app validates against must be the issuer the browser was sent to.** They agree by accident in development, where both are `localhost:8081`, and disagree in a container deployment, where the browser sees a public URL and the app sees a service name. The symptom is a redirect loop that names nothing; the fix is `KC_HOSTNAME` and `HARBOR_OIDC_ISSUER_URI` set to the same public URL.

## 11. Build & CI

- `./run.sh test` and `verify` export `DOCKER_HOST` from the active docker context when it is not already set. The CLI reads contexts and Testcontainers does not, so on Colima the CLI works while the tests report no Docker environment.
- `./run.sh env` brings up the whole development stack in one task and names no service: which containers Harbor needs is `environment/dev/compose.yaml`'s decision, so adding one there needs no change to `run.sh`.
- Run build / test / frontend tasks through `./run.sh <task>` (`env`, `deps`, `compile`, `bundle`, `styles`, `test`, `verify`, `run`, `package`, `clean`), which pins JDK 21. Never invoke `mvn` directly. Every task but `deps` builds offline, so a newly added dependency needs `./run.sh deps` once. After a `@CssImport(themeFor=…)` / `@JsModule` change run `./run.sh bundle`; after editing an `@import`-ed CSS partial run `./run.sh styles`.
- `./run.sh verify` clears the cached bundles before building. A `dev.bundle` left by `./run.sh run` makes the frontend build report "a production mode bundle build is not needed", and the integration tests then open a page whose client bundle fails to boot.
- CI runs `mvn verify` on push (Temurin JDK 21).
- Tests live alongside the package they cover. Unit tests for the service layer, browserless tests for views, `*IT` Playwright tests for whole journeys. Both of the latter stub `MetadataResolver` so nothing reaches the network. Anything touching the library needs a real PostgreSQL — import `HarborDatabase` and empty the table in `@BeforeEach`, since one database now serves the whole suite.
- **Every Spring test names a profile.** `@ActiveProfiles("test")` for everything that must not reach a network, `@ActiveProfiles("journey")` for `HarborJourneyIT`, which authenticates for real. Both files carry what the context will not start without: the archiving browser (the application refuses to start with none configured) and, for the `test` profile, an OAuth2 client — without a `ClientRegistrationRepository`, `oauth2LoginPage(...)` fails while the filter chain is being built.
- *Profile* files deliberately, not `application.properties` under `src/test/resources`: that name shadows the main file wholesale and takes the datasource and Flyway settings with it.
- **A profile can override a key but never remove one, and an issuer beats explicit endpoints.** `application.properties` defaults `provider.keycloak.issuer-uri` to the development realm, and Boot resolves an issuer by fetching its discovery document at startup — so a test that inherits it hangs the whole context on `localhost:8081`. Blanking the value does not help either; an empty issuer fails as "issuer cannot be empty". The `test` profile therefore points the registration at a second provider (`offline`) whose endpoints cannot resolve, leaving the inherited issuer unreferenced. The registration id stays `keycloak`, because that is what builds the callback path and what `SecurityConfig` names.
- The tier that wants a real identity provider does not share that profile. `HarborJourneyIT` uses `journey`, which leaves the main OAuth2 config alone, and supplies the issuer and client through `@DynamicPropertySource` from the Keycloak it starts. Undoing another profile's settings is the shape to avoid.
- **Verify a configuration change against the real property files.** An `ApplicationContextRunner` with no `@ActiveProfiles` and no `ConfigDataApplicationContextInitializer` loads none of them, so it will happily confirm a registration that the actual suite cannot build. Both of the OIDC startup failures in this repository's history got past exactly that kind of check.
- Identity is stubbed in every tier but one. Import `StubIdentityConfiguration` for a `@Primary LibraryOwner` whose reader is switchable — which is what makes owner isolation testable at all — and call its static `authenticate(…)` where the code under test goes through navigation access control: a browserless test bypasses the servlet filter chain but not `SpringNavigationAccessControl`.
- Two tests reach a real container of their own rather than stubbing. `BrowserPageArchiverTest` starts Chromium through `ArchivingBrowser`, and `-Dharbor.archive.browser-url=…` points it at your own browser instead where containers cannot run. `HarborJourneyIT` starts Keycloak through `HarborIdentity`, which creates its own realm over the admin REST API and hands Spring its own client id and secret, and drives the real login form. A test tier owns the data it needs and shares nothing with `environment/dev/` — the development realm is free to differ. That is the only place the OAuth2 redirect, the token exchange, the subject and the logout are exercised for real, and it makes the test a four-container one and the most fragile in the suite.
- JaCoCo `<includes>` for a `PACKAGE` rule take dot notation (`io.binarycodes.harbor.*.service`). Slash notation matches nothing and the gate silently passes.
- **Surefire and failsafe pass `@{argLine}`, and `argLine` has an empty default in `<properties>`.** JaCoCo's prepare-agent overwrites that property with the coverage agent, so anything the plugins add to `argLine` — the Mockito agent, currently — has to be appended late or it replaces the coverage agent and the gate then measures nothing. The empty default is what keeps `-Djacoco.skip=true` working: with no property to resolve, `@{argLine}` reaches the forked JVM literally and it refuses to start. After touching either argLine, check that `target/jacoco.exec` is still written and that the gate still *fails* for an under-covered run — a detached agent looks exactly like a passing build.
- Session-scoped beans cannot be `@Autowired` into a browserless test's fields — the Vaadin session does not exist that early. Take the `ApplicationContext` and resolve them in `@BeforeEach`.
- Conventional Commits: `<type>[(scope)][!]: <description>`, type one of `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`. Subject ≤100 chars, single line, no body, no `Co-Authored-By`. A `commit-msg` hook in `.githooks/` enforces this; enable per clone with `git config core.hooksPath .githooks`.

## 12. Working principles

- Verify each change visually or by test before declaring done.
- Preserve look-and-feel during refactors; a pixel change is a separate task.
- Refactor in small focused steps — extract, rename, run tests, then move on.
- **The README describes the application as it is now**, not as it was or as it might become. No migration notes, no historical asides, no upgrade paths for a scenario an earlier version left behind. Behaviour the code still performs is current state and belongs there — the import of a library from browser storage, for instance, because `LegacyLibraryImport` still runs it on first open. Advice for data nobody has is not. When a feature changes or a concern is dropped, the README text that existed only to serve it goes in the same change.
