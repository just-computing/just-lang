package org.justlang.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class ArgsParserTest {
    @Test
    void bareInputPathUsesBuildShortcut() {
        ArgsParser parser = new ArgsParser();
        Command command = parser.parse(new String[] { "examples/hello.just" });
        assertTrue(command instanceof BuildCommand);
    }

    @Test
    void buildShortcutSupportsOutputFlag() {
        ArgsParser parser = new ArgsParser();
        Command command = parser.parse(new String[] { "examples/hello.just", "--out", "tmp/out.jar" });
        assertTrue(command instanceof BuildCommand);
    }

    @Test
    void unknownVerbStillReturnsUsageCommand() {
        ArgsParser parser = new ArgsParser();
        Command command = parser.parse(new String[] { "unknown", "arg" });
        assertTrue(command instanceof HelpCommand);
    }
}
