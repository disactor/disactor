package com.disactor;

public class EventImpl<T extends EventImpl<T>> implements Event<T> {

    private final Trace trace = new Trace();

    public Trace getTrace() {
        return trace;
    }

    public void copyFrom(T from) {
        trace.copyFrom(from.getTrace());
    }
}
