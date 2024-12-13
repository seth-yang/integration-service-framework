package org.dreamwork.integration.api;

final public class Param<T> {
    public final String name;
    public final T value;

    public Param (String name, T value) {
        if (value == null || name == null || name.trim ().length () == 0) {
            throw new NullPointerException ();
        }

        this.name = name;
        this.value = value;
    }
}
