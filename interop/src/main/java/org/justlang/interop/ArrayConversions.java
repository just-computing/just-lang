package org.justlang.interop;

import java.util.List;
import org.justlang.stdlib.Vec;

public final class ArrayConversions {
    public <T> Vec<T> toJust(JvmListRef<T> value) {
        Vec<T> vec = Vec.newVec();
        List<T> list = value.get();
        for (T item : list) {
            vec.push(item);
        }
        return vec;
    }
}
