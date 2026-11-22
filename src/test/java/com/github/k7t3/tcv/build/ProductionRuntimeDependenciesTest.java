package com.github.k7t3.tcv.build;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ProductionRuntimeDependenciesTest {
    @Test
    void theShippedRuntimeDoesNotCarryTheUnusedLegacyConfigurationLibraries() {
        var classpath = System.getProperty("tcv.test.production.classpath");
        assertThat(classpath).as("the actual production runtime, separate from test-only SDK dependencies").isNotBlank();
        var artifacts = Arrays.stream(classpath.split(File.pathSeparator))
                .map(entry -> Path.of(entry).getFileName().toString()).toList();

        assertThat(artifacts).noneMatch(name -> name.startsWith("commons-configuration-")
                || name.startsWith("commons-lang-") || name.startsWith("commons-logging-"));
    }
}
