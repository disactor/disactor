package com.disactor.lazy;

import com.disactor.Event;
import com.disactor.Trace;

public class MagicEvent<T extends MagicEvent<T>> extends MagicCopyable<T> implements Event<T> {

    private final Trace trace = new Trace();

    @Override
    public Trace getTrace() {
        return trace;
    }

}
