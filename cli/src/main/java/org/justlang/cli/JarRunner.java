package org.justlang.cli;

import java.nio.file.Path;

public final class JarRunner {
    public int runJar(Path jarPath) {
        try {
            Process process = new ProcessBuilder("java", "-jar", jarPath.toString())
                .inheritIO()
                .start();
            return process.waitFor();
        } catch (Exception error) {
            System.err.println("Failed to run jar: " + error.getMessage());
            return 1;
        }
    }
}
