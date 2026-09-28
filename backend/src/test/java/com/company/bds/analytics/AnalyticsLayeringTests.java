package com.company.bds.analytics;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PROJECT_CODE_RULES §4.3 for the analytics module until S9's ArchUnit rules cover every module: application depends on
 * domain and its own ports only (no infrastructure, no HTTP types); domain depends on nothing framework-specific.
 */
class AnalyticsLayeringTests {
    private static final Path MODULE = Path.of("src/main/java/com/company/bds/analytics");

    @Test
    void applicationLayerUsesPortsNotInfrastructureOrHttpTypes() throws IOException {
        assertThat(importsIn("application", "com.company.bds.analytics.infrastructure", "com.company.bds.analytics.api",
                "com.company.bds.shared.error", "org.springframework.web", "org.springframework.http", "jakarta.servlet")).isEmpty();
    }

    @Test
    void domainLayerHasNoFrameworkDependencies() throws IOException {
        assertThat(importsIn("domain", "org.springframework", "com.fasterxml", "jakarta.", "com.company.bds.analytics.application",
                "com.company.bds.analytics.infrastructure")).isEmpty();
    }

    private static List<String> importsIn(String layer, String... forbidden) throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MODULE.resolve(layer))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                for (String line : Files.readAllLines(file)) {
                    if (!line.startsWith("import ")) continue;
                    for (String prefix : forbidden) {
                        if (line.startsWith("import " + prefix) || line.startsWith("import static " + prefix)) {
                            violations.add(MODULE.relativize(file) + ": " + line);
                        }
                    }
                }
            }
        }
        return violations;
    }
}
