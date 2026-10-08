package io.agenticawithakka;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Domain contracts, ports and tools must not depend on frameworks, transports or vendor SDKs (ARC-01). */
class ArchitectureBoundaryTest {
    // Matches imports and fully qualified references alike.
    private static final Pattern FORBIDDEN = Pattern.compile(
            "\\b(akka|org\\.springframework|jakarta\\.servlet|tools\\.jackson|com\\.fasterxml"
                    + "|io\\.agenticawithakka\\.(api|config|agents|connectors|persistence))\\.[a-z]");

    @ParameterizedTest
    @ValueSource(strings = {"domain", "application/ports", "tools"})
    void boundaryPackagesImportNoFrameworkOrAdapterTypes(String packagePath) throws IOException {
        var root = Path.of("src/main/java/io/agenticawithakka", packagePath);
        List<Path> sources;
        try (Stream<Path> files = Files.walk(root)) {
            sources = files.filter(p -> p.toString().endsWith(".java")).toList();
        }
        assertThat(sources).isNotEmpty();
        for (Path source : sources) {
            assertThat(FORBIDDEN.matcher(Files.readString(source)).find())
                    .as("forbidden import in %s", source)
                    .isFalse();
        }
    }
}
