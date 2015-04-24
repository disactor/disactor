package com.disactor;

import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.ExceptionHandler;
import com.lmax.disruptor.dsl.Disruptor;

import java.util.concurrent.Executor;

public class Disactor<EVENT extends Event<EVENT>> implements Actor<EVENT>{


    private final Disruptor<EVENT> disruptor;

    public Disactor(
            EventFactory<EVENT> eventFactory,
            int bufferSize,
            ExceptionHandler<EVENT> exceptionHandler,
            Executor executor,
            Actor<EVENT>... actors) {
        this(new Disruptor<>(eventFactory, bufferSize, executor), exceptionHandler, actors);
    }

    public Disactor(Disruptor<EVENT> disruptor, ExceptionHandler<EVENT> exceptionHandler, Actor<EVENT>[] actors) {
        this.disruptor = disruptor;
        disruptor.handleExceptionsWith(exceptionHandler);
        for (final Actor<EVENT> actor : actors) {
            //noinspection unchecked
            disruptor.handleEventsWith(new EventHandler<EVENT>() {
                @Override
                public void onEvent(EVENT event, long sequence, boolean endOfBatch) throws Exception {
                    actor.onEvent(event);
                }
            });
        }
    }

    public void start() {
        disruptor.start();
    }

    public void stop() {
        disruptor.shutdown();
    }

    public void onEvent(EVENT event) {
        long seq = disruptor.getRingBuffer().next();
        EVENT nextEvent = disruptor.getRingBuffer().get(seq);
        nextEvent.copyFrom(event);
        disruptor.getRingBuffer().publish(seq);
    }
}
