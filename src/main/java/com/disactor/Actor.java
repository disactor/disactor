package com.disactor;

public interface Actor<EVENT extends Event<EVENT>> {

    void onEvent(EVENT event);

}
