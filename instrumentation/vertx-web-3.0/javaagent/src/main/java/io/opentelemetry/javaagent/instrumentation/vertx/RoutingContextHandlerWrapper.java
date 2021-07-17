/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.ServerSpan;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This is used to wrap Vert.x Handlers to provide nice user-friendly SERVER span names */
public final class RoutingContextHandlerWrapper implements Handler<RoutingContext> {

  private static final Logger logger = LoggerFactory.getLogger(RoutingContextHandlerWrapper.class);

  private final Handler<RoutingContext> handler;

  public RoutingContextHandlerWrapper(Handler<RoutingContext> handler) {
    this.handler = handler;
  }

  @Override
  public void handle(RoutingContext context) {
    Span serverSpan = ServerSpan.fromContextOrNull(Context.current());
    try {
      if (serverSpan != null) {
        // TODO should update only SERVER span using
        // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/465
        serverSpan.updateName(context.currentRoute().getPath());
      }
    } catch (RuntimeException ex) {
      logger.error("Failed to update server span name with vert.x route", ex);
    }
    try {
      handler.handle(context);
    } catch (Throwable throwable) {
      if (serverSpan != null) {
        serverSpan.recordException(unwrapThrowable(throwable));
      }
      throw throwable;
    }
  }

  private static Throwable unwrapThrowable(Throwable throwable) {
    if (throwable.getCause() != null
        && (throwable instanceof ExecutionException
            || throwable instanceof CompletionException
            || throwable instanceof InvocationTargetException
            || throwable instanceof UndeclaredThrowableException)) {
      return unwrapThrowable(throwable.getCause());
    }
    return throwable;
  }
}
