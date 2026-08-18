package io.binarycodes.harbor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A real PostgreSQL for the tests that need one. The queries carry the search,
 * the tag filter and the ICU collation, so anything standing in for the database
 * would only be testing the stand-in.
 *
 * <p>Normally that means a throwaway container. Where containers cannot run — a
 * host without nested virtualisation, for one — point the build at a database of
 * your own instead:
 *
 * <pre>
 * ./run.sh verify -Dharbor.test.database=external \
 *     -Dspring.datasource.url=jdbc:postgresql://host:5432/harbor_test \
 *     -Dspring.datasource.username=… -Dspring.datasource.password=…
 * </pre>
 *
 * <p>Whatever it points at gets its {@code bookmark} table emptied between tests,
 * so it must not be a database anyone cares about.
 */
@TestConfiguration(proxyBeanMethods = false)
public class HarborDatabase {

    static final String IMAGE = "postgres:18-alpine";

    @Bean
    @ServiceConnection
    @ConditionalOnProperty(name = "harbor.test.database", havingValue = "container", matchIfMissing = true)
    PostgreSQLContainer harborPostgres() {
        return new PostgreSQLContainer(IMAGE);
    }
}
