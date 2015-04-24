package com.disactor;

public interface Copyable<T extends Copyable<T>> {

    void copyFrom(T from);

}
