package org.justlang.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProjectManifest {
    private final String name;
    private final String main;
    private final Map<String, String> dependencyPaths;

    private ProjectManifest(String name, String main, Map<String, String> dependencyPaths) {
        this.name = name;
        this.main = main;
        this.dependencyPaths = Map.copyOf(dependencyPaths);
    }

    public String name() {
        return name;
    }

    public String main() {
        return main;
    }

    public Map<String, String> dependencyPaths() {
        return dependencyPaths;
    }

    public Map<String, Path> dependencyRoots(Path projectRoot) {
        Map<String, Path> roots = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : dependencyPaths.entrySet()) {
            Path resolved = projectRoot.resolve(entry.getValue()).normalize();
            Path srcDir = resolved.resolve("src");
            if (Files.isDirectory(srcDir)) {
                roots.put(entry.getKey(), srcDir);
                continue;
            }
            roots.put(entry.getKey(), resolved);
        }
        return roots;
    }

    public static ProjectManifest load(Path manifestPath) throws IOException {
        List<String> lines = Files.readAllLines(manifestPath);
        String name = null;
        String main = null;
        Map<String, String> dependencyPaths = new LinkedHashMap<>();
        String section = "";
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                section = trimmed.substring(1, trimmed.length() - 1).trim();
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = trimmed.substring(0, eq).trim();
            String value = trimmed.substring(eq + 1).trim();
            if ("dependencies".equals(section)) {
                String dependencyPath = parseDependencyPath(value);
                if (dependencyPath != null) {
                    dependencyPaths.put(key, dependencyPath);
                }
                continue;
            }

            value = stripQuotes(value);
            if ("name".equals(key)) {
                name = value;
            } else if ("main".equals(key)) {
                main = value;
            }
        }
        return new ProjectManifest(name, main, dependencyPaths);
    }

    private static String parseDependencyPath(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return stripQuotes(trimmed);
        }
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return null;
        }
        String body = trimmed.substring(1, trimmed.length() - 1).trim();
        String[] parts = body.split(",");
        for (String part : parts) {
            String segment = part.trim();
            int eq = segment.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = segment.substring(0, eq).trim();
            String val = segment.substring(eq + 1).trim();
            if ("path".equals(key)) {
                return stripQuotes(val);
            }
        }
        return null;
    }

    private static String stripQuotes(String value) {
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
