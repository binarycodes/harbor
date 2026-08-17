package io.binarycodes.harbor.security;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;

/**
 * Harbor has no accounts: every bookmark is stored in the visitor's own browser,
 * so there is nothing to authenticate against and no per-user data on the
 * server. Spring Security is still configured explicitly, because leaving it on
 * the classpath unconfigured would put the whole application behind the default
 * generated login form.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * A year, the shortest max-age browsers accept for HSTS preloading.
     */
    private static final Duration HSTS_MAX_AGE = Duration.ofDays(365);

    /**
     * Harbor asks the browser for none of these. Denying them outright means a
     * script that somehow reaches the page cannot reach the camera or the location
     * either.
     */
    private static final String DENIED_FEATURES = "accelerometer=(), camera=(), display-capture=(),"
            + " geolocation=(), gyroscope=(), magnetometer=(), microphone=(), midi=(), payment=(),"
            + " serial=(), usb=()";

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http.with(VaadinSecurityConfigurer.vaadin(), vaadin -> vaadin
                        .anyRequest(request -> request.permitAll())
                        .enableNavigationAccessControl(false))
                .headers(headers -> {
                    headers.referrerPolicy(referrer -> referrer
                            .policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                    headers.crossOriginOpenerPolicy(opener -> opener
                            .policy(CrossOriginOpenerPolicy.SAME_ORIGIN));
                    headers.permissionsPolicyHeader(permissions -> permissions.policy(DENIED_FEATURES));
                    headers.httpStrictTransportSecurity(hsts -> hsts
                            .includeSubDomains(true)
                            .maxAgeInSeconds(HSTS_MAX_AGE.toSeconds()));
                    // Spring Security normally writes its headers as the response
                    // commits, which never happens for the response Vaadin renders the
                    // page into — static resources got the headers and the application's
                    // own routes got none. Writing them eagerly covers both.
                    headers.addObjectPostProcessor(new ObjectPostProcessor<HeaderWriterFilter>() {

                        @Override
                        public <O extends HeaderWriterFilter> O postProcess(O filter) {
                            filter.setShouldWriteHeadersEagerly(true);
                            return filter;
                        }
                    });
                })
                .build();
    }
}
