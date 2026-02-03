package org.justlang.runtime;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.Optional;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

public final class FfmLinker {
    private final Linker linker;
    private final Arena arena;
    private final SymbolLookup lookup;
    private MethodHandle mallocHandle;
    private MethodHandle reallocHandle;
    private MethodHandle freeHandle;
    private MethodHandle memcpyHandle;

    public FfmLinker() {
        this.linker = Linker.nativeLinker();
        this.arena = Arena.ofShared();
        this.lookup = createLookup();
    }

    public void loadLibc() {
        // No-op for now. Lookup is created eagerly.
    }

    public MethodHandle mallocHandle() {
        if (mallocHandle == null) {
            mallocHandle = downcall("malloc", FunctionDescriptor.of(ADDRESS, JAVA_LONG));
        }
        return mallocHandle;
    }

    public MethodHandle reallocHandle() {
        if (reallocHandle == null) {
            reallocHandle = downcall("realloc", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_LONG));
        }
        return reallocHandle;
    }

    public MethodHandle freeHandle() {
        if (freeHandle == null) {
            freeHandle = downcall("free", FunctionDescriptor.ofVoid(ADDRESS));
        }
        return freeHandle;
    }

    public MethodHandle memcpyHandle() {
        if (memcpyHandle == null) {
            memcpyHandle = downcall("memcpy", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_LONG));
        }
        return memcpyHandle;
    }

    private SymbolLookup createLookup() {
        SymbolLookup loader = SymbolLookup.loaderLookup();
        SymbolLookup libc;
        try {
            libc = SymbolLookup.libraryLookup("c", arena);
        } catch (IllegalArgumentException ex) {
            libc = name -> Optional.empty();
        }
        SymbolLookup finalLibc = libc;
        return name -> {
            Optional<MemorySegment> symbol = finalLibc.find(name);
            return symbol.isPresent() ? symbol : loader.find(name);
        };
    }

    private MethodHandle downcall(String name, FunctionDescriptor descriptor) {
        MemorySegment symbol = lookup.find(name)
            .orElseThrow(() -> new IllegalStateException("Missing libc symbol: " + name));
        return linker.downcallHandle(symbol, descriptor);
    }
}
