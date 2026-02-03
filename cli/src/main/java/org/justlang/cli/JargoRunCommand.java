package org.justlang.cli;

import java.nio.file.Path;

public final class JargoRunCommand implements Command {
    private final Path projectRoot;

    public JargoRunCommand(Path projectRoot) {
        this.projectRoot = projectRoot;
    }

    @Override
    public int run() {
        JargoBuildCommand build = new JargoBuildCommand(projectRoot);
        int result = build.run();
        if (result != 0) {
            return result;
        }
        Path jarPath = projectRoot.resolve("build/just.jar");
        return new JarRunner().runJar(jarPath);
    }
}
