package com.disactor;

public class Trace extends RuntimeException implements Copyable<Trace> {

    private final Trace[] nested;
    private int lastN;

    private final Trace parent;

    public Trace() {
        this(Integer.valueOf(System.getProperty("com.disactor.trace.depth", "1024")));
    }

    public Trace(int depth) {
        this.parent = null;
        this.nested = new Trace[depth];
        for (int i = 0; i < nested.length; i++) {
            nested[i] = new Trace(this);
        }
    }

    public Trace(Trace parent) {
        this.nested = null;
        this.parent = parent;
        parent.nest(this);
    }

    public void nest(Trace child) {
        if (isParent()) {
            this.nested[lastN++] = child;
        } else {
            parent.nest(child);
        }
    }

    public boolean isParent() {
        return this.nested != null;
    }

    public void copyFrom(Trace from) {
        if (from.parent != null) {
            this.parent.copyFrom(from.parent);
        } else {
            for (int i = 0; i < nested.length; i++) {
                nested[i].copyFrom(from.nested[i]);
            }
        }
    }

}
