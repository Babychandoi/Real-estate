package com.company.bds.shared.uat;

import com.company.bds.shared.config.SchedulingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Review 2 m6: a seeder JVM never runs the job worker or business schedulers. */
class UatSeedModeTests {

    @Test
    void seedModeSwitchesOffTheWorkerMetricsRefresherAndSchedulersWhateverElseIsConfigured() {
        StandardEnvironment environment = environment(Map.of("app.uat-seed.mode", "seed", "app.jobs.enabled", "true",
                "app.scheduling.enabled", "true"));

        new UatSeedModeEnvironmentPostProcessor().postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("app.jobs.enabled")).isEqualTo("false");
        assertThat(environment.getProperty("app.jobs.metrics.enabled")).isEqualTo("false");
        assertThat(environment.getProperty("app.scheduling.enabled")).isEqualTo("false");
        assertThat(environment.getProperty("app.search.sync-on-startup")).isEqualTo("false");
    }

    @Test
    void normalRunsAreLeftAlone() {
        StandardEnvironment environment = environment(Map.of("app.jobs.enabled", "true"));

        new UatSeedModeEnvironmentPostProcessor().postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getPropertySources().contains(UatSeedModeEnvironmentPostProcessor.SOURCE_NAME)).isFalse();
        assertThat(environment.getProperty("app.jobs.enabled")).isEqualTo("true");
    }

    @Test
    void postProcessorIsRegisteredAndSchedulingFollowsTheFlag() throws Exception {
        StringBuilder factories = new StringBuilder();
        var resources = getClass().getClassLoader().getResources("META-INF/spring.factories");
        while (resources.hasMoreElements()) {
            try (var in = resources.nextElement().openStream()) { factories.append(new String(in.readAllBytes())).append('\n'); }
        }
        assertThat(factories.toString().replaceAll("\\\\\\s+", ""))
                .contains(EnvironmentPostProcessor.class.getName() + "=" + UatSeedModeEnvironmentPostProcessor.class.getName());
        ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(SchedulingConfig.class);
        runner.run(context -> assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class));
        runner.withPropertyValues("app.scheduling.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class));
    }

    private static StandardEnvironment environment(Map<String, Object> properties) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("commandLine", properties));
        return environment;
    }
}
