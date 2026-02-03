package org.justlang.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class FmtCommand implements Command {
    private final Path inputPath;

    public FmtCommand(Path inputPath) {
        this.inputPath = inputPath;
    }

    @Override
    public int run() {
        List<Path> files = collectFiles();
        int formatted = 0;

        for (Path file : files) {
            try {
                String original = Files.readString(file);
                String formattedText = format(original);
                if (!formattedText.equals(original)) {
                    Files.writeString(file, formattedText);
                    formatted++;
                }
            } catch (IOException error) {
                System.err.println("Failed to format " + file + ": " + error.getMessage());
                return 1;
            }
        }

        System.out.println("Formatted " + formatted + " file(s)");
        return 0;
    }

    private List<Path> collectFiles() {
        List<Path> files = new ArrayList<>();
        if (Files.isDirectory(inputPath)) {
            try (Stream<Path> paths = Files.walk(inputPath)) {
                paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".just"))
                    .forEach(files::add);
            } catch (IOException error) {
                throw new RuntimeException("Failed to scan " + inputPath, error);
            }
        } else {
            files.add(inputPath);
        }
        return files;
    }

    private String format(String input) {
        String[] lines = input.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = stripTrailingWhitespace(lines[i]);
            builder.append(line);
            if (i < lines.length - 1) {
                builder.append('\n');
            }
        }
        if (builder.length() == 0 || builder.charAt(builder.length() - 1) != '\n') {
            builder.append('\n');
        }
        return builder.toString();
    }

    private String stripTrailingWhitespace(String line) {
        int end = line.length();
        while (end > 0 && Character.isWhitespace(line.charAt(end - 1))) {
            end--;
        }
        return line.substring(0, end);
    }
}
