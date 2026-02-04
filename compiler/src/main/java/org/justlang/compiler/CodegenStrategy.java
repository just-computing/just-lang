package org.justlang.compiler;

import java.util.List;

public interface CodegenStrategy {
    List<ClassFile> emit(AstModule module);

    String mainClassName();
}
