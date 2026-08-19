import java.net.HttpURLConnection;
import java.net.URI;

/**
 * A readiness probe for a container whose image has no curl and no wget. Keycloak
 * ships a JVM, so the probe is a single Java source file run straight from the
 * command line — which is what makes this work where a shell one-liner cannot.
 *
 * <p>Not a port check. Keycloak binds its ports well before the realm is usable, and
 * a probe that passes while the thing behind it is unreachable is worse than none.
 * This asks {@code /health/ready} and reports what it actually answered.
 *
 * <p>Plain HTTP only, deliberately. The development stack is loopback inside one
 * container, so an HTTPS branch here would only be a trust-all TLS bypass sitting in
 * the repository waiting to be copied somewhere it matters.
 */
public class HealthCheck {

    private static final int UNSUPPORTED_SCHEME = 2;

    public static void main(String[] arguments) throws Exception {
        URI endpoint = new URI(arguments[0]);
        if (!"http".equals(endpoint.getScheme())) {
            System.err.println("HealthCheck speaks plain http only, not " + endpoint.getScheme());
            System.exit(UNSUPPORTED_SCHEME);
        }
        HttpURLConnection connection = (HttpURLConnection) endpoint.toURL().openConnection();
        System.exit(connection.getResponseCode() == HttpURLConnection.HTTP_OK ? 0 : 1);
    }
}
