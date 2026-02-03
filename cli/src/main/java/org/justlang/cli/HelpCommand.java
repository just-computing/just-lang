package org.justlang.cli;

public final class HelpCommand implements Command {
    private final String error;

    private HelpCommand(String error) {
        this.error = error;
    }

    public static HelpCommand usage(String error) {
        return new HelpCommand(error);
    }

    @Override
    public int run() {
        if (error != null) {
            System.err.println(error);
            System.err.println();
        }
        System.err.println("Usage:");
        System.err.println("  just build <file.just|dir> [--out <jarPath>]");
        System.err.println("  just run <file.just|dir>");
        System.err.println("  just check <file.just|dir>");
        System.err.println("  just jargo new <name>");
        return 2;
    }
}
