package org.justlang.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public final class JarEmitter {
    public void writeJar(List<ClassFile> classFiles, Path output, String mainClass) throws IOException {
        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (mainClass != null && !mainClass.isBlank()) {
            attributes.put(Attributes.Name.MAIN_CLASS, mainClass);
        }

        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(output), manifest)) {
            for (ClassFile classFile : classFiles) {
                String entryName = classFile.internalName() + ".class";
                JarEntry entry = new JarEntry(entryName);
                jar.putNextEntry(entry);
                jar.write(classFile.bytes());
                jar.closeEntry();
            }
        }
    }
}
