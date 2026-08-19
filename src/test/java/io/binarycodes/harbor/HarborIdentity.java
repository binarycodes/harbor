package io.binarycodes.harbor;

import java.time.Duration;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * A real Keycloak, for the one tier that can drive a login form. Started the same way
 * {@link HarborDatabase} starts PostgreSQL and {@link ArchivingBrowser} starts
 * Chromium — a throwaway container, so {@code ./run.sh verify} needs no orchestration
 * of its own.
 *
 * <p>One container for the whole suite, started on first use, and Testcontainers takes
 * it down at the end.
 *
 * <p>The realm is the same file the development stack imports rather than a copy under
 * {@code src/test/resources}: two definitions of one realm drift, and the one that
 * drifts is the one nobody logs into by hand.
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
     * The user id pinned in the realm export, which is the {@code sub} in every token
     * this Keycloak issues and therefore the {@code owner_id} of every row the journeys
     * write. A test that has to authenticate its own thread — the cleanup that empties
     * the library between journeys — needs to know it before anyone has logged in.
     */
    public static final String SUBJECT = "9f6b6a1c-2d4e-4f80-9a3b-5c7d8e1f0a24";

    private static final String REALM_EXPORT = "environment/dev/keycloak/harbor-realm.json";

    private static final int HTTP_PORT = 8080;

    private static GenericContainer<?> container;

    private HarborIdentity() {
    }

    /**
     * Where the realm's OIDC endpoints are, for
     * {@code spring.security.oauth2.client.provider.keycloak.issuer-uri}.
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
                    .withCommand("start-dev", "--import-realm")
                    .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
                    .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
                    // Maven runs from the project root, in CI too.
                    .withCopyFileToContainer(MountableFile.forHostPath(REALM_EXPORT),
                            "/opt/keycloak/data/import/harbor-realm.json")
                    // The realm's own discovery document, not the port and not
                    // Keycloak's health: the import finishes after Keycloak reports
                    // ready, and the realm having imported is what these tests depend
                    // on.
                    .waitingFor(Wait.forHttp("/realms/" + REALM + "/.well-known/openid-configuration")
                            .forPort(HTTP_PORT)
                            .withStartupTimeout(Duration.ofMinutes(3)));
            container.start();
        }
        return "http://" + container.getHost() + ":" + container.getMappedPort(HTTP_PORT)
                + "/realms/" + REALM;
    }
}
