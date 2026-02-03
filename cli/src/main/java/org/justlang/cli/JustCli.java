package org.justlang.cli;

public final class JustCli {
    public static void main(String[] args) {
        ArgsParser parser = new ArgsParser();
        Command command = parser.parse(args);
        int exitCode = command.run();
        System.exit(exitCode);
    }
}
