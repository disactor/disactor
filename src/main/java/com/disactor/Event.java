package com.disactor;

public interface Event<T extends Event<T>> extends Copyable<T> {

    Trace getTrace();

}
