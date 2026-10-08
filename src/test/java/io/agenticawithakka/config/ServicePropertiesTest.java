package io.agenticawithakka.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class ServicePropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void acceptsConfiguredServiceName() {
        runner.withPropertyValues("app.service-name=local-lab").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ServiceProperties.class).serviceName()).isEqualTo("local-lab");
        });
    }

    @Test
    void missingNameFailsStartup() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "Invalid_Name", "a-name-that-is-far-too-long-to-be-a-valid-public-service-identifier-here"})
    void invalidNameFailsStartup(String name) {
        runner.withPropertyValues("app.service-name=" + name).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("serviceName");
        });
    }

    @Test
    void unknownAppSettingFailsStartup() {
        runner.withPropertyValues("app.service-name=local-lab", "app.servce-name=typo")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ServiceProperties.class)
    static class TestConfiguration {
    }
}
