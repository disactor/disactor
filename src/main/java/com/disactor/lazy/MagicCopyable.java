package com.disactor.lazy;

import com.disactor.Copyable;

public class MagicCopyable<T extends Copyable<T>> implements Copyable<T> {

    @Override
    public void copyFrom(T from) {

    }

}
