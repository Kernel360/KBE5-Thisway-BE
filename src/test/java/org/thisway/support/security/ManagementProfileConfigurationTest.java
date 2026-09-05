package org.thisway.support.security;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ManagementProfileConfigurationTest {

    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @ParameterizedTest
    @ValueSource(strings = {"dev", "prod"})
    void 관리_endpoint는_health와_prometheus만_노출한다(String profile) throws IOException {
        List<PropertySource<?>> propertySources = loader.load(
                profile,
                new ClassPathResource("application-%s.yml".formatted(profile))
        );

        assertThat(propertySources)
                .extracting(source -> source.getProperty("management.endpoints.web.exposure.include"))
                .containsExactly("health,prometheus");
        assertThat(propertySources)
                .extracting(source -> source.getProperty("management.endpoint.health.show-details"))
                .containsExactly("never");
        assertThat(propertySources)
                .extracting(source -> source.getProperty("management.endpoint.prometheus.enabled"))
                .containsExactly(true);
        assertThat(propertySources)
                .extracting(source -> source.getProperty("management.prometheus.metrics.export.enabled"))
                .containsExactly(true);
        assertThat(propertySources)
                .extracting(source -> source.getProperty("spring.security.debug"))
                .containsExactly(false);
        assertThat(propertySources)
                .extracting(source -> source.getProperty("logging.level.org.springframework.security"))
                .containsExactly("INFO");
        assertThat(propertySources)
                .extracting(source -> source.getProperty("logging.level.org.springframework.web"))
                .containsExactly("INFO");
    }
}
