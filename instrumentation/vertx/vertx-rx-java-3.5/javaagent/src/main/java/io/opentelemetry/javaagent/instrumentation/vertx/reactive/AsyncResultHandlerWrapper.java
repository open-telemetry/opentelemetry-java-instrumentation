/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.reactive;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AsyncResultHandlerWrapper implements Handler<Handler<AsyncResult<?>>> {

  private static final Logger logger = Logger.getLogger(AsyncResultHandlerWrapper.class.getName());

  private final Handler<Handler<AsyncResult<?>>> delegate;
  private final Context executionContext;

  public AsyncResultHandlerWrapper(
      Handler<Handler<AsyncResult<?>>> delegate, Context executionContext) {
    this.delegate = delegate;
    this.executionContext = executionContext;
  }

  @Override
  public void handle(Handler<AsyncResult<?>> asyncResultHandler) {
    if (executionContext != null) {
      try (Scope ignored = executionContext.makeCurrent()) {
        delegate.handle(asyncResultHandler);
      }
    } else {
      delegate.handle(asyncResultHandler);
    }
  }

  public static Handler<Handler<AsyncResult<?>>> wrapIfNeeded(
      Handler<Handler<AsyncResult<?>>> delegate, Context executionContext) {
    if (!(delegate instanceof AsyncResultHandlerWrapper)) {
      logger.log(Level.FINE, "Wrapping handler {0}", delegate);
      return new AsyncResultHandlerWrapper(delegate, executionContext);
    }
    return delegate;
  }
}
