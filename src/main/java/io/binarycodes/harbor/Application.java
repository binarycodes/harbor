package io.binarycodes.harbor;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import io.binarycodes.harbor.library.service.OutboundFetchProperties;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.theme.aura.Aura;

/**
 * Harbor — a local-first research library. Bookmarks live in the browser's local
 * storage, so the application keeps no server-side database.
 */
@SpringBootApplication
@EnableConfigurationProperties(OutboundFetchProperties.class)
@Push
@StyleSheet(Aura.STYLESHEET)
@StyleSheet("styles.css")
public class Application implements AppShellConfigurator {

    public static void main(String[] arguments) {
        SpringApplication.run(Application.class, arguments);
    }

    /**
     * Injected rather than read statically so that tests can fix the moment a
     * bookmark was saved and assert on the resulting order.
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
