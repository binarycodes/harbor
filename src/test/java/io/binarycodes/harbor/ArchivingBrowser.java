package io.binarycodes.harbor;

import java.time.Duration;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * A real browser for the tests that archive. Started the same way
 * {@link HarborDatabase} starts PostgreSQL — a throwaway container, so that
 * {@code ./run.sh test} needs no orchestration of its own.
 *
 * <p>One container for the whole suite, started on first use. Starting a browser per
 * test class would dominate the build, and Testcontainers takes it down at the end
 * of the run.
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

    private static GenericContainer<?> container;

    private ArchivingBrowser() {
    }

    public static synchronized String url() {
        String external = System.getProperty(EXTERNAL);
        if (external != null && !external.isBlank()) {
            return external;
        }
        if (container == null) {
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
}
