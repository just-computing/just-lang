package org.justlang.compiler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class SourceLoader {
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^\\s*import\\s+\"([^\"]+)\"\\s*;\\s*$");

    public List<SourceFile> load(Project project) {
        List<SourceFile> sources = new ArrayList<>();
        Path root = project.root();

        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".just"))
                .forEach(path -> sources.add(read(path)));
        } catch (IOException error) {
            throw new RuntimeException("Failed to scan sources under " + root, error);
        }

        return sources;
    }

    /**
     * Loads an entry file plus its transitive {@code import "...";} dependencies.
     *
     * <p><b>Algorithm</b>: a depth-first traversal (DFS) of the import graph, emitting files in
     * postorder (dependencies first). This yields a deterministic topological-like ordering for
     * acyclic graphs:
     *
     * <ul>
     *   <li>{@code ordered} is a {@link LinkedHashMap} used as an insertion-ordered set of visited files
     *       (ensures stable output across runs).</li>
     *   <li>{@code onStack} tracks the active DFS recursion stack to detect import cycles.</li>
     *   <li>{@code stack} is used only to format a user-facing cycle path in diagnostics.</li>
     * </ul>
     *
     * <p>Imports are resolved relative to the importing file's parent directory.
     *
     * <p>Complexity: {@code O(V + E)} file visits + import edges (ignoring IO).
     */
    public List<SourceFile> loadFileGraph(Path entryPath) {
        Map<Path, SourceFile> ordered = new LinkedHashMap<>();
        Set<Path> onStack = new HashSet<>();
        Deque<Path> stack = new ArrayDeque<>();
        loadRecursive(entryPath.toAbsolutePath().normalize(), ordered, onStack, stack);
        return new ArrayList<>(ordered.values());
    }

    public SourceFile loadFile(Path path) {
        return read(path);
    }

    /**
     * DFS step for {@link #loadFileGraph(Path)}.
     *
     * <p>This is a standard "visited + recursion stack" graph walk:
     * <ul>
     *   <li>If {@code ordered} already contains {@code path}, it was fully processed and is skipped.</li>
     *   <li>If {@code onStack} already contains {@code path}, a cycle exists and compilation fails.</li>
     *   <li>Otherwise we read the file, recursively load its imports, and then insert it into {@code ordered}
     *       (postorder) so that dependencies appear before dependents.</li>
     * </ul>
     */
    private void loadRecursive(
        Path path,
        Map<Path, SourceFile> ordered,
        Set<Path> onStack,
        Deque<Path> stack
    ) {
        if (ordered.containsKey(path)) {
            return;
        }
        if (!Files.exists(path)) {
            throw new RuntimeException("Missing imported source file: " + path);
        }
        if (onStack.contains(path)) {
            throw new RuntimeException("Import cycle detected: " + formatCycle(path, stack));
        }

        onStack.add(path);
        stack.push(path);
        SourceFile source = read(path);
        for (String importPath : parseImports(source.contents())) {
            Path resolved = path.getParent().resolve(importPath).normalize();
            loadRecursive(resolved, ordered, onStack, stack);
        }
        stack.pop();
        onStack.remove(path);
        ordered.put(path, source);
    }

    private List<String> parseImports(String contents) {
        List<String> imports = new ArrayList<>();
        String[] lines = contents.split("\\R");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("//")) {
                continue;
            }
            Matcher matcher = IMPORT_PATTERN.matcher(trimmed);
            if (matcher.matches()) {
                imports.add(matcher.group(1));
            }
        }
        return imports;
    }

    private String formatCycle(Path repeatedPath, Deque<Path> stack) {
        List<Path> current = new ArrayList<>(stack);
        java.util.Collections.reverse(current);
        int start = current.indexOf(repeatedPath);
        if (start < 0) {
            return repeatedPath.toString();
        }
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < current.size(); i++) {
            if (i > start) {
                builder.append(" -> ");
            }
            builder.append(current.get(i));
        }
        builder.append(" -> ").append(repeatedPath);
        return builder.toString();
    }

    private SourceFile read(Path path) {
        try {
            return new SourceFile(path, Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new RuntimeException("Failed to read source file " + path, error);
        }
    }
}
