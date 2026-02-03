package org.justlang.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class SourceLoader {
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

    public SourceFile loadFile(Path path) {
        return read(path);
    }

    private SourceFile read(Path path) {
        try {
            return new SourceFile(path, Files.readString(path));
        } catch (IOException error) {
            throw new RuntimeException("Failed to read source file " + path, error);
        }
    }
}
