package org.justlang.runtime;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DropGlue {
    private final Map<Integer, DropHandler> handlers = new ConcurrentHashMap<>();

    public void register(int dropId, DropHandler handler) {
        handlers.put(dropId, handler);
    }

    public void drop(long address, int dropId) {
        DropHandler handler = handlers.get(dropId);
        if (handler == null) {
            throw new IllegalStateException("Unknown drop handler: " + dropId);
        }
        handler.drop(address);
    }

    public interface DropHandler {
        void drop(long address);
    }
}
