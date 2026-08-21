package io.binarycodes.harbor;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A real Keycloak, for the one tier that can drive a login form. Started the same way
 * {@link HarborDatabase} starts PostgreSQL and {@link ArchivingBrowser} starts
 * Chromium — a throwaway container, so {@code ./run.sh verify} needs no orchestration
 * of its own.
 *
 * <p>One container for the whole suite, started on first use, and Testcontainers takes
 * it down at the end.
 *
 * <p>The realm is created afterwards over the admin REST API rather than imported from
 * an export at boot: a wrong field then arrives as an HTTP 400 that names it, instead of
 * as a container that refuses to start.
 *
 * <p>Everything the realm contains is defined here, and this fixture hands the client
 * id and secret to Spring itself — so a journey depends on nothing outside its own
 * containers. In particular it shares nothing with the development realm that
 * {@code environment/dev/keycloak/init.mjs} builds. The two describe the same shape
 * because Harbor needs that shape, not because either reads the other, and they are
 * free to differ.
 */
public final class HarborIdentity {

    /**
     * Pinned so an upgrade is a commit rather than a surprise, exactly as the browser
     * and the database images are.
     */
    static final String IMAGE = "quay.io/keycloak/keycloak:26.4";

    public static final String REALM = "harbor";
    public static final String USERNAME = "reader";
    public static final String PASSWORD = "reader";

    /**
     * What the realm's firstName and lastName below add up to, which is the name Harbor
     * shows the reader. Kept beside them so the two cannot drift apart.
     */
    public static final String FULL_NAME = "Harbor Reader";

    private static String readerSubject;

    /**
     * The client this fixture registers, and the secret it registers with. Handed to
     * Spring by {@code HarborJourneyIT} rather than read from a properties file, so the
     * only agreement that has to hold is between this class and the Keycloak it started.
     * Deliberately not the development realm's secret: nothing here should look like it
     * has to match anything outside this container.
     */
    public static final String CLIENT_ID = "harbor";
    public static final String CLIENT_SECRET = "journey-test-secret";

    private static final int HTTP_PORT = 8080;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static GenericContainer<?> container;

    private HarborIdentity() {
    }

    /**
     * Where the realm's OIDC endpoints are, for
     * {@code spring.security.oauth2.client.provider.oidc.issuer-uri}.
     *
     * <p>A mapped port is right here and wrong in a deployment: under {@code start-dev}
     * Keycloak derives the issuer from the request host, and both the test JVM and
     * Playwright's browser are on the host, so both see the same one. In a container
     * deployment they do not, which is what {@code KC_HOSTNAME} is for.
     */
    public static synchronized String issuerUri() {
        if (container == null) {
            container = new GenericContainer<>(DockerImageName.parse(IMAGE))
                    .withExposedPorts(HTTP_PORT)
                    .withCommand("start-dev")
                    .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
                    .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
                    // The built-in realm's discovery document, not the port: Keycloak
                    // binds long before it can answer, and a wait that passes while the
                    // thing behind it is unreachable is worse than none.
                    .waitingFor(Wait.forHttp("/realms/master/.well-known/openid-configuration")
                            .forPort(HTTP_PORT)
                            .withStartupTimeout(Duration.ofMinutes(3)));
            container.start();
            createRealm(baseUrl());
        }
        return baseUrl() + "/realms/" + REALM;
    }

    /**
     * The reader's {@code sub}, which is the {@code owner_id} of every row the journeys
     * write. Read back from Keycloak rather than chosen here: the admin API assigns the id
     * itself and silently ignores one supplied on create, so a value picked in advance
     * belongs to nobody. Authenticating the cleanup thread as that nobody emptied nothing,
     * left every journey's bookmarks behind, and turned the second save of a URL into a
     * duplicate — which surfaced as the reader screen never opening.
     */
    public static synchronized String subject() {
        issuerUri();
        return readerSubject;
    }

    private static String baseUrl() {
        return "http://" + container.getHost() + ":" + container.getMappedPort(HTTP_PORT);
    }

    private static void createRealm(String baseUrl) {
        String token = adminToken(baseUrl);
        post(baseUrl + "/admin/realms", token, """
                {
                  "realm": "%s",
                  "enabled": true,
                  "sslRequired": "none"
                }
                """.formatted(REALM));
        post(baseUrl + "/admin/realms/" + REALM + "/clients", token, """
                {
                  "clientId": "%s",
                  "secret": "%s",
                  "enabled": true,
                  "protocol": "openid-connect",
                  "publicClient": false,
                  "standardFlowEnabled": true,
                  "directAccessGrantsEnabled": false,
                  "redirectUris": ["*"],
                  "webOrigins": ["*"],
                  "attributes": { "post.logout.redirect.uris": "*" }
                }
                """.formatted(CLIENT_ID, CLIENT_SECRET));
        // A complete profile, and it has to be complete: Keycloak's VERIFY_PROFILE
        // action fires on login for a user missing a required attribute, and holds the
        // browser on a "complete your account" form that looks exactly like a login that
        // did not take. firstName and lastName are required by the default user profile.
        // requiredActions is empty for the same reason, stated rather than assumed.
        post(baseUrl + "/admin/realms/" + REALM + "/users", token, """
                {
                  "username": "%s",
                  "firstName": "Harbor",
                  "lastName": "Reader",
                  "email": "%s@harbor.invalid",
                  "emailVerified": true,
                  "enabled": true,
                  "requiredActions": [],
                  "credentials": [{ "type": "password", "value": "%s", "temporary": false }]
                }
                """.formatted(USERNAME, USERNAME, PASSWORD));
        readerSubject = lookUpReaderId(baseUrl, token);
    }

    private static String lookUpReaderId(String baseUrl, String token) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/admin/realms/" + REALM + "/users?exact=true&username="
                        + USERNAME))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        JsonNode found = JSON.readTree(send(request, "look up the reader's id").body());
        if (!found.isArray() || found.isEmpty()) {
            throw new IllegalStateException(
                    "Keycloak reports no user '" + USERNAME + "' just after creating one");
        }
        return found.get(0).path("id").asString();
    }

    private static String adminToken(String baseUrl) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/realms/master/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "grant_type=password&client_id=admin-cli&username=admin&password=admin"))
                .build();
        return JSON.readTree(send(request, "authenticate as the Keycloak admin").body())
                .path("access_token")
                .asString();
    }

    private static void post(String url, String token, String body) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        send(request, "POST " + url);
    }

    /**
     * Anything other than success is fatal, and says what came back. A realm that was
     * half created fails later as a login that does not work, which is a long way from
     * the cause.
     */
    private static HttpResponse<String> send(HttpRequest request, String describe) {
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("Could not " + describe + ": HTTP "
                        + response.statusCode() + " " + response.body());
            }
            return response;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while setting up Keycloak", interrupted);
        } catch (IOException failed) {
            throw new IllegalStateException("Could not " + describe, failed);
        }
    }
}
