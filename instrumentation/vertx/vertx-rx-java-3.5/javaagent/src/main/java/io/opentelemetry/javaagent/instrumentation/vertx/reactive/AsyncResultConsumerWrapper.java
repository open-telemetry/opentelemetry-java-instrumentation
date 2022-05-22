/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.reactive;

import static java.util.logging.Level.FINE;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class AsyncResultConsumerWrapper implements Consumer<Handler<AsyncResult<?>>> {

  private static final Logger logger = Logger.getLogger(AsyncResultConsumerWrapper.class.getName());

  private final Consumer<Handler<AsyncResult<?>>> delegate;
  private final Context executionContext;

  public AsyncResultConsumerWrapper(
      Consumer<Handler<AsyncResult<?>>> delegate, Context executionContext) {
    this.delegate = delegate;
    this.executionContext = executionContext;
  }

  @Override
  public void accept(Handler<AsyncResult<?>> asyncResultHandler) {
    if (executionContext != null) {
      try (Scope ignored = executionContext.makeCurrent()) {
        delegate.accept(asyncResultHandler);
      }
    } else {
      delegate.accept(asyncResultHandler);
    }
  }

  public static Consumer<Handler<AsyncResult<?>>> wrapIfNeeded(
      Consumer<Handler<AsyncResult<?>>> delegate, Context executionContext) {
    if (!(delegate instanceof AsyncResultConsumerWrapper)) {
      logger.log(FINE, "Wrapping consumer {0}", delegate);
      return new AsyncResultConsumerWrapper(delegate, executionContext);
    }
    return delegate;
  }
}
