/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.reactive;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncResultHandlerWrapper implements Handler<Handler<AsyncResult<?>>> {

  private static final Logger logger = LoggerFactory.getLogger(AsyncResultHandlerWrapper.class);

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
      logger.debug("Wrapping handler {}", delegate);
      return new AsyncResultHandlerWrapper(delegate, executionContext);
    }
    return delegate;
  }
}
