package org.justlang.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SourceLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void loadFileGraphLoadsTransitiveImportsInDependencyOrder() throws IOException {
        Path main = tempDir.resolve("main.just").toAbsolutePath().normalize();
        Path service = tempDir.resolve("service.just").toAbsolutePath().normalize();
        Path model = tempDir.resolve("model.just").toAbsolutePath().normalize();

        Files.writeString(main, """
            import "service.just";
            import "model.just";

            fn main() { return; }
            """);
        Files.writeString(service, """
            import "model.just";

            fn helper() { return; }
            """);
        Files.writeString(model, "fn model_fn() { return; }\n");

        SourceLoader loader = new SourceLoader();
        List<SourceFile> sources = loader.loadFileGraph(main);

        List<Path> loaded = sources.stream().map(SourceFile::path).toList();
        assertEquals(List.of(model, service, main), loaded);
    }

    @Test
    void loadFileGraphFailsWhenImportDoesNotExist() throws IOException {
        Path main = tempDir.resolve("main.just");
        Files.writeString(main, """
            import "missing.just";

            fn main() { return; }
            """);

        SourceLoader loader = new SourceLoader();
        RuntimeException error = assertThrows(RuntimeException.class, () -> loader.loadFileGraph(main));
        assertTrue(error.getMessage().contains("Missing imported source file:"));
    }

    @Test
    void loadFileGraphFailsOnImportCycle() throws IOException {
        Path a = tempDir.resolve("a.just");
        Path b = tempDir.resolve("b.just");
        Files.writeString(a, """
            import "b.just";

            fn a() { return; }
            """);
        Files.writeString(b, """
            import "a.just";

            fn b() { return; }
            """);

        SourceLoader loader = new SourceLoader();
        RuntimeException error = assertThrows(RuntimeException.class, () -> loader.loadFileGraph(a));
        assertTrue(error.getMessage().contains("Import cycle detected:"));
    }

    @Test
    void loadFileGraphSupportsModDeclarations() throws IOException {
        Path main = tempDir.resolve("main.just");
        Path featureDir = tempDir.resolve("feature");
        Files.createDirectories(featureDir);
        Path util = featureDir.resolve("util.just");

        Files.writeString(main, """
            mod feature::util;

            fn main() { return; }
            """);
        Files.writeString(util, "fn helper() { return; }\n");

        SourceLoader loader = new SourceLoader();
        List<SourceFile> sources = loader.loadFileGraph(main);
        List<Path> loaded = sources.stream().map(SourceFile::path).toList();
        assertEquals(List.of(util.toAbsolutePath().normalize(), main.toAbsolutePath().normalize()), loaded);
    }

    @Test
    void loadFileGraphSupportsRustModDeclarations() throws IOException {
        Path main = tempDir.resolve("main.rs");
        Path featureDir = tempDir.resolve("feature");
        Files.createDirectories(featureDir);
        Path util = featureDir.resolve("util.rs");

        Files.writeString(main, """
            mod feature::util;

            fn main() { return; }
            """);
        Files.writeString(util, "fn helper() { return; }\n");

        SourceLoader loader = new SourceLoader();
        List<SourceFile> sources = loader.loadFileGraph(main);
        List<Path> loaded = sources.stream().map(SourceFile::path).toList();
        assertEquals(List.of(util.toAbsolutePath().normalize(), main.toAbsolutePath().normalize()), loaded);
    }

    @Test
    void loadFileGraphSupportsRustModRsFiles() throws IOException {
        Path main = tempDir.resolve("main.rs");
        Path featureDir = tempDir.resolve("feature");
        Files.createDirectories(featureDir);
        Path modFile = featureDir.resolve("mod.rs");

        Files.writeString(main, """
            mod feature;

            fn main() { return; }
            """);
        Files.writeString(modFile, "fn helper() { return; }\n");

        SourceLoader loader = new SourceLoader();
        List<SourceFile> sources = loader.loadFileGraph(main);
        List<Path> loaded = sources.stream().map(SourceFile::path).toList();
        assertEquals(List.of(modFile.toAbsolutePath().normalize(), main.toAbsolutePath().normalize()), loaded);
    }

    @Test
    void loadProjectIncludesRustSources() throws IOException {
        Path projectRoot = tempDir.resolve("project");
        Path nested = projectRoot.resolve("nested");
        Files.createDirectories(nested);
        Path main = projectRoot.resolve("main.rs");
        Path helper = nested.resolve("helper.rs");
        Files.writeString(main, "fn main() { return; }\n");
        Files.writeString(helper, "fn helper() { return; }\n");

        SourceLoader loader = new SourceLoader();
        List<SourceFile> sources = loader.load(new Project(projectRoot));

        Set<Path> loaded = sources.stream().map(SourceFile::path).collect(java.util.stream.Collectors.toSet());
        assertEquals(
            Set.of(main.toAbsolutePath().normalize(), helper.toAbsolutePath().normalize()),
            loaded
        );
    }

    @Test
    void loadFileGraphResolvesDependencyAliasImports() throws IOException {
        Path depRoot = tempDir.resolve("dep");
        Files.createDirectories(depRoot);
        Path depFile = depRoot.resolve("math.just");
        Files.writeString(depFile, "fn double(x: i32) -> i32 { return x * 2; }\n");
        Path main = tempDir.resolve("main.just");
        Files.writeString(main, """
            import "@utils/math.just";
            fn main() { return; }
            """);

        SourceLoader loader = new SourceLoader();
        List<SourceFile> sources = loader.loadFileGraph(main, Map.of("utils", depRoot));
        List<Path> loaded = sources.stream().map(SourceFile::path).toList();
        assertEquals(List.of(depFile.toAbsolutePath().normalize(), main.toAbsolutePath().normalize()), loaded);
    }

    @Test
    void loadFileGraphFailsForUnknownDependencyAlias() throws IOException {
        Path main = tempDir.resolve("main.just");
        Files.writeString(main, """
            import "@unknown/math.just";
            fn main() { return; }
            """);

        SourceLoader loader = new SourceLoader();
        RuntimeException error = assertThrows(RuntimeException.class, () -> loader.loadFileGraph(main, Map.of()));
        assertTrue(error.getMessage().contains("Unknown dependency alias in import"));
    }
}
