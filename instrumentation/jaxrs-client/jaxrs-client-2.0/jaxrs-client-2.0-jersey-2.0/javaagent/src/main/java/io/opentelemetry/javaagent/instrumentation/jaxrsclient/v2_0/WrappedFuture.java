/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0;

import static io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0.JerseyClientUtil.handleException;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.glassfish.jersey.client.ClientRequest;

public class WrappedFuture<T> implements Future<T> {

  private final Future<T> wrapped;
  private final ClientRequest context;

  public WrappedFuture(Future<T> wrapped, ClientRequest context) {
    this.wrapped = wrapped;
    this.context = context;
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    return wrapped.cancel(mayInterruptIfRunning);
  }

  @Override
  public boolean isCancelled() {
    return wrapped.isCancelled();
  }

  @Override
  public boolean isDone() {
    return wrapped.isDone();
  }

  @Override
  public T get() throws InterruptedException, ExecutionException {
    try {
      return wrapped.get();
    } catch (ExecutionException e) {
      handleException(context, e.getCause());
      throw e;
    }
  }

  @Override
  public T get(long timeout, TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    try {
      return wrapped.get(timeout, unit);
    } catch (ExecutionException e) {
      handleException(context, e.getCause());
      throw e;
    }
  }
}
