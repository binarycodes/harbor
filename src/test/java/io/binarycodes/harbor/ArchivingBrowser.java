package io.binarycodes.harbor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.time.Duration;

import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * A real browser for the tests that archive, and an address it can fetch their
 * fixtures from. Started the same way {@link HarborDatabase} starts PostgreSQL — a
 * throwaway container, so {@code ./run.sh test} needs no orchestration of its own.
 *
 * <p>One container for the whole suite, started on first use. Starting a browser per
 * test class would dominate the build, and Testcontainers takes it down at the end.
 *
 * <p>Where containers cannot run — a host without nested virtualisation, for one —
 * {@code -Dharbor.archive.browser-url=…} points the tests at a browser of your own
 * instead, exactly as {@code -Dharbor.test.database=external} does for the database.
 */
public final class ArchivingBrowser {

    /**
     * chromedp's image rather than one of ours: Chromium binds DevTools to loopback
     * whatever {@code --remote-debugging-address} says, and this carries the forwarder
     * that works around it. Pinned so an upgrade is a commit rather than a surprise.
     */
    static final String IMAGE = "chromedp/headless-shell:151.0.7922.109";

    private static final String EXTERNAL = "harbor.archive.browser-url";

    /**
     * The port a test serves its fixtures on. Fixed for the run rather than ephemeral
     * per test, because the browser can only be told about it before it starts and it
     * starts once: an ephemeral port would be chosen after that door had closed.
     */
    private static final int FIXTURE_PORT = freePort();

    private static GenericContainer<?> container;

    private ArchivingBrowser() {
    }

    /**
     * Bind fixture servers here, so the browser knows where to look.
     */
    public static int fixturePort() {
        return FIXTURE_PORT;
    }

    /**
     * Where the browser should look for a fixture served by this JVM.
     *
     * <p>Not {@code 127.0.0.1} when the browser is in a container: that is the
     * container's own loopback, so the fixture is simply not there and the browser
     * prints a blank page — which looks like a working archive right up to the byte
     * count.
     */
    public static String fixtureAddress() {
        return isExternal()
                ? "127.0.0.1:" + FIXTURE_PORT
                : "host.testcontainers.internal:" + FIXTURE_PORT;
    }

    public static synchronized String url() {
        if (isExternal()) {
            return System.getProperty(EXTERNAL);
        }
        if (container == null) {
            // Before the container exists, not after: this is what puts
            // host.testcontainers.internal into its hosts file and stands up the
            // forwarder. Exposing a port to a container already running does nothing,
            // which is a silent failure — the browser reaches nothing and prints a
            // blank page.
            Testcontainers.exposeHostPorts(FIXTURE_PORT);
            container = new GenericContainer<>(DockerImageName.parse(IMAGE))
                    .withExposedPorts(9222)
                    // Chromium's scratch space; the 64 MB default is not enough for it.
                    .withSharedMemorySize(512L * 1024 * 1024)
                    .waitingFor(Wait.forHttp("/json/version")
                            .forPort(9222)
                            .withStartupTimeout(Duration.ofSeconds(60)));
            container.start();
        }
        return "http://" + container.getHost() + ":" + container.getMappedPort(9222);
    }

    private static boolean isExternal() {
        String external = System.getProperty(EXTERNAL);
        return external != null && !external.isBlank();
    }

    /**
     * A port nothing is using, released again immediately. The window between letting
     * it go and binding it is a race in theory; in a test run on one machine it is not
     * one worth defending against.
     */
    private static int freePort() {
        try (ServerSocket probe = new ServerSocket(0)) {
            return probe.getLocalPort();
        } catch (IOException noPort) {
            throw new UncheckedIOException("No free port for the fixture server", noPort);
        }
    }
}
