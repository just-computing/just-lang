package org.justlang.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class CompilerFixtureKit {
    private static final String FIXTURE_ROOT = "fixtures";

    private CompilerFixtureKit() {}

    static List<FixtureCase> discoverFixtures() throws IOException {
        Path root = fixtureRoot();
        List<FixtureCase> fixtures = new ArrayList<>();
        try (Stream<Path> paths = Files.list(root)) {
            List<Path> directories = paths
                .filter(Files::isDirectory)
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .toList();
            for (Path directory : directories) {
                fixtures.add(loadFixture(directory));
            }
        }
        assertTrue(!fixtures.isEmpty(), "No compiler fixtures found under " + root);
        return fixtures;
    }

    static void executeFixture(FixtureCase fixture) throws Exception {
        Path outputJar = null;
        try {
            if (fixture.command() != FixtureCommand.CHECK) {
                outputJar = Files.createTempFile("just-fixture-" + fixture.name() + "-", ".jar");
            }

            CompileResult compileResult = compile(fixture, outputJar);
            assertEquals(
                fixture.expectedSuccess(),
                compileResult.success(),
                () -> "Unexpected compile success for fixture '" + fixture.name() + "'. Diagnostics:\n" + diagnosticsSummary(compileResult.diagnostics())
            );

            assertDiagnostics(fixture, compileResult.diagnostics());
            assertBuildArtifact(fixture, compileResult, outputJar);

            if (fixture.command() == FixtureCommand.RUN && compileResult.success()) {
                ProcessOutput runOutput = runJar(outputJar);
                assertEquals(
                    0,
                    runOutput.exitCode(),
                    () -> "java -jar failed for fixture '" + fixture.name() + "'. stderr:\n" + normalize(runOutput.stderr())
                );
                assertExpectedOutput(fixture, runOutput);
            }
        } finally {
            if (outputJar != null) {
                Files.deleteIfExists(outputJar);
            }
        }
    }

    private static CompileResult compile(FixtureCase fixture, Path outputJar) {
        JustCompiler compiler = new JustCompiler();
        CompileRequest request = switch (fixture.command()) {
            case CHECK -> CompileRequest.forCheck(fixture.sourcePath());
            case BUILD, RUN -> CompileRequest.forBuild(fixture.sourcePath(), outputJar);
        };
        return compiler.compile(request);
    }

    private static void assertBuildArtifact(FixtureCase fixture, CompileResult compileResult, Path outputJar) throws IOException {
        if (fixture.command() == FixtureCommand.CHECK) {
            return;
        }
        if (!compileResult.success()) {
            return;
        }
        assertTrue(outputJar != null, "Missing output jar path for fixture '" + fixture.name() + "'");
        assertTrue(Files.exists(outputJar), "Expected output jar was not created for fixture '" + fixture.name() + "'");
        assertTrue(Files.size(outputJar) > 0, "Output jar is empty for fixture '" + fixture.name() + "'");
    }

    private static void assertDiagnostics(FixtureCase fixture, List<Diagnostic> diagnostics) {
        if (fixture.expectedDiagnostics() == null || fixture.expectedDiagnostics().isBlank()) {
            return;
        }

        List<String> messages = diagnostics.stream().map(Diagnostic::message).toList();
        if (fixture.diagnosticMode() == DiagnosticMatchMode.EXACT) {
            assertEquals(
                normalize(fixture.expectedDiagnostics()),
                normalize(String.join("\n", messages)),
                "Diagnostic output mismatch for fixture '" + fixture.name() + "'"
            );
            return;
        }

        List<String> expectedSnippets = fixture.expectedDiagnostics()
            .lines()
            .map(String::trim)
            .filter(line -> !line.isEmpty() && !line.startsWith("#"))
            .toList();
        for (String snippet : expectedSnippets) {
            assertTrue(
                messages.stream().anyMatch(message -> message.contains(snippet)),
                () -> "Missing diagnostic snippet '" + snippet + "' for fixture '" + fixture.name() + "'. Diagnostics:\n"
                    + diagnosticsSummary(diagnostics)
            );
        }
    }

    private static void assertExpectedOutput(FixtureCase fixture, ProcessOutput output) {
        if (fixture.expectedStdout() != null) {
            assertEquals(
                normalize(fixture.expectedStdout()),
                normalize(output.stdout()),
                "stdout mismatch for fixture '" + fixture.name() + "'"
            );
        }
        if (fixture.expectedStderr() != null) {
            assertEquals(
                normalize(fixture.expectedStderr()),
                normalize(output.stderr()),
                "stderr mismatch for fixture '" + fixture.name() + "'"
            );
        }
    }

    private static ProcessOutput runJar(Path jarPath) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(javaExecutable().toString(), "-jar", jarPath.toString()).start();
        byte[] stdoutBytes = process.getInputStream().readAllBytes();
        byte[] stderrBytes = process.getErrorStream().readAllBytes();
        int exitCode = process.waitFor();
        return new ProcessOutput(
            exitCode,
            new String(stdoutBytes, StandardCharsets.UTF_8),
            new String(stderrBytes, StandardCharsets.UTF_8)
        );
    }

    private static Path javaExecutable() {
        String executable = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable);
    }

    private static FixtureCase loadFixture(Path fixtureDir) throws IOException {
        Path sourcePath = fixtureDir.resolve("main.just");
        Path specPath = fixtureDir.resolve("expect.properties");
        assertTrue(Files.exists(sourcePath), "Missing fixture source file: " + sourcePath);
        assertTrue(Files.exists(specPath), "Missing fixture spec file: " + specPath);

        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(specPath)) {
            properties.load(in);
        }

        FixtureCommand command = FixtureCommand.parse(required(properties, "command"));
        boolean success = Boolean.parseBoolean(required(properties, "success"));
        DiagnosticMatchMode diagnosticMode = DiagnosticMatchMode.parse(properties.getProperty("diagnosticsMatch", "contains"));
        if (command == FixtureCommand.RUN && !success) {
            throw new IllegalStateException("RUN fixture must expect success: " + fixtureDir.getFileName());
        }

        return new FixtureCase(
            fixtureDir.getFileName().toString(),
            sourcePath,
            command,
            success,
            diagnosticMode,
            readOptionalText(fixtureDir.resolve("diagnostics.txt")),
            readOptionalText(fixtureDir.resolve("stdout.txt")),
            readOptionalText(fixtureDir.resolve("stderr.txt"))
        );
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required fixture property '" + key + "'");
        }
        return value.trim();
    }

    private static String readOptionalText(Path path) throws IOException {
        if (!Files.exists(path)) {
            return null;
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static Path fixtureRoot() {
        java.net.URL url = CompilerFixtureKit.class.getClassLoader().getResource(FIXTURE_ROOT);
        if (url == null) {
            throw new IllegalStateException("Missing test resource directory '" + FIXTURE_ROOT + "'");
        }
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException error) {
            throw new IllegalStateException("Invalid fixture resource URI: " + url, error);
        }
    }

    private static String diagnosticsSummary(List<Diagnostic> diagnostics) {
        return diagnostics.stream()
            .map(diagnostic -> diagnostic.path() + ": " + diagnostic.message())
            .collect(Collectors.joining("\n"));
    }

    private static String normalize(String text) {
        return text.replace("\r\n", "\n").stripTrailing();
    }

    enum FixtureCommand {
        CHECK,
        BUILD,
        RUN;

        static FixtureCommand parse(String value) {
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "check" -> CHECK;
                case "build" -> BUILD;
                case "run" -> RUN;
                default -> throw new IllegalStateException("Unknown fixture command: " + value);
            };
        }
    }

    enum DiagnosticMatchMode {
        CONTAINS,
        EXACT;

        static DiagnosticMatchMode parse(String value) {
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "contains" -> CONTAINS;
                case "exact" -> EXACT;
                default -> throw new IllegalStateException("Unknown diagnosticsMatch value: " + value);
            };
        }
    }

    record ProcessOutput(int exitCode, String stdout, String stderr) {}

    record FixtureCase(
        String name,
        Path sourcePath,
        FixtureCommand command,
        boolean expectedSuccess,
        DiagnosticMatchMode diagnosticMode,
        String expectedDiagnostics,
        String expectedStdout,
        String expectedStderr
    ) {}
}
