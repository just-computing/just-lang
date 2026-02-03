package org.justlang.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ProjectManifest {
    private final String name;
    private final String main;

    private ProjectManifest(String name, String main) {
        this.name = name;
        this.main = main;
    }

    public String name() {
        return name;
    }

    public String main() {
        return main;
    }

    public static ProjectManifest load(Path manifestPath) throws IOException {
        List<String> lines = Files.readAllLines(manifestPath);
        String name = null;
        String main = null;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = trimmed.substring(0, eq).trim();
            String value = trimmed.substring(eq + 1).trim();
            value = stripQuotes(value);
            if ("name".equals(key)) {
                name = value;
            } else if ("main".equals(key)) {
                main = value;
            }
        }
        return new ProjectManifest(name, main);
    }

    private static String stripQuotes(String value) {
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
