package org.justlang.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class JargoNewCommand implements Command {
    private final String name;

    public JargoNewCommand(String name) {
        this.name = name;
    }

    @Override
    public int run() {
        Path root = Path.of(name).toAbsolutePath().normalize();
        Path srcDir = root.resolve("src");
        Path mainFile = srcDir.resolve("main.just");
        Path appFile = srcDir.resolve("app.just");
        Path manifest = root.resolve("just.toml");
        Path readme = root.resolve("README.md");

        try {
            Files.createDirectories(srcDir);
            if (!Files.exists(manifest)) {
                Files.writeString(manifest, "name = \"" + name + "\"\nmain = \"src/main.just\"\n");
            }
            if (!Files.exists(mainFile)) {
                Files.writeString(
                    mainFile,
                    "mod app;\n"
                        + "use app::app_greeting;\n\n"
                        + "fn main() {\n"
                        + "    std::print(app_greeting());\n"
                        + "    return;\n"
                        + "}\n"
                );
            }
            if (!Files.exists(appFile)) {
                Files.writeString(
                    appFile,
                    "pub fn app_greeting() -> String {\n"
                        + "    return \"hello from " + name + "\";\n"
                        + "}\n"
                );
            }
            if (!Files.exists(readme)) {
                Files.writeString(
                    readme,
                    "# " + name + "\n\n"
                        + "## Build\n\n"
                        + "```bash\n"
                        + "just jargo build\n"
                        + "```\n\n"
                        + "## Run\n\n"
                        + "```bash\n"
                        + "just jargo run\n"
                        + "```\n"
                );
            }
        } catch (IOException error) {
            System.err.println("Failed to create project: " + error.getMessage());
            return 1;
        }

        System.out.println("Created project at " + root);
        return 0;
    }
}
