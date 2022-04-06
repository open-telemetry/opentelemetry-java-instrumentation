package io.opentelemetry.javaagent.instrumentation.methods;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

public abstract class AbstractWithTraceSpanTest<T, U> {

  protected static final IllegalArgumentException FAILURE = new IllegalArgumentException("Boom");

  @WithSpa
  protected abstract T completable() {
    return SettableFuture.create();
  }

  @WithSpan
  ListenableFuture<String> alreadySucceeded() {
    return Futures.immediateFuture("Value");
  }

  @WithSpan
  ListenableFuture<String> alreadyFailed() {
    return Futures.immediateFailedFuture(FAILURE);
  }
}
