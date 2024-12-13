package org.dreamwork.embedded.redis.utils;

import java.util.Collection;
import java.util.Map;

public class Function {
    public static boolean isEmpty (Collection<?> c) {
        return c == null || c.isEmpty ();
    }

    public static boolean isEmpty (Map<?, ?> map) {
        return map == null || map.isEmpty ();
    }

    public static boolean isNotEmpty (Collection<?> c) {
        return c != null && !c.isEmpty ();
    }

    public static boolean isNotEmpty (Map<?, ?> map) {
        return map != null && !map.isEmpty ();
    }
}
