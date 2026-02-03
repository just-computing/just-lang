package org.justlang.cli;

public final class ArgsParser {
    public Command parse(String[] args) {
        if (args.length == 0) {
            return HelpCommand.usage(null);
        }

        String command = args[0];
        if ("jargo".equals(command)) {
            return parseJargo(args);
        }

        if ("build".equals(command)) {
            return parseBuild(args);
        }

        if ("run".equals(command)) {
            return parseRun(args);
        }

        if ("check".equals(command)) {
            return parseCheck(args);
        }

        return HelpCommand.usage("Unknown command: " + command);
    }

    private Command parseBuild(String[] args) {
        java.nio.file.Path inputPath = null;
        java.nio.file.Path outputJar = null;

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if ("-o".equals(arg) || "--out".equals(arg)) {
                if (i + 1 >= args.length) {
                    return HelpCommand.usage("Missing value for " + arg);
                }
                outputJar = java.nio.file.Path.of(args[++i]);
                continue;
            }

            if (inputPath == null) {
                inputPath = PathResolver.resolveInput(arg);
                continue;
            }

            return HelpCommand.usage("Unexpected argument: " + arg);
        }

        if (inputPath == null) {
            return HelpCommand.usage("Missing input file or directory.");
        }

        if (outputJar == null) {
            java.nio.file.Path base = java.nio.file.Files.isDirectory(inputPath)
                ? inputPath
                : inputPath.getParent();
            outputJar = base.resolve("build/just.jar");
        }

        return new BuildCommand(inputPath, outputJar);
    }

    private Command parseRun(String[] args) {
        if (args.length < 2) {
            return HelpCommand.usage("Missing input file for run.");
        }
        if (args.length > 2) {
            return HelpCommand.usage("Unexpected arguments for run.");
        }
        java.nio.file.Path inputPath = PathResolver.resolveInput(args[1]);
        return new RunCommand(inputPath);
    }

    private Command parseCheck(String[] args) {
        if (args.length < 2) {
            return HelpCommand.usage("Missing input file or directory for check.");
        }
        if (args.length > 2) {
            return HelpCommand.usage("Unexpected arguments for check.");
        }
        java.nio.file.Path inputPath = PathResolver.resolveInput(args[1]);
        return new CheckCommand(inputPath);
    }

    private Command parseJargo(String[] args) {
        if (args.length < 2) {
            return HelpCommand.usage("Missing jargo subcommand.");
        }
        String subcommand = args[1];
        if ("new".equals(subcommand)) {
            if (args.length < 3) {
                return HelpCommand.usage("Missing project name for jargo new.");
            }
            if (args.length > 3) {
                return HelpCommand.usage("Unexpected arguments for jargo new.");
            }
            return new JargoNewCommand(args[2]);
        }
        return HelpCommand.usage("Unknown jargo subcommand: " + subcommand);
    }
}
