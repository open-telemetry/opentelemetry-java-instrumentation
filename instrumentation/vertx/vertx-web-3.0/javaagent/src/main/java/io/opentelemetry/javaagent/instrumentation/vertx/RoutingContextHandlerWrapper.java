/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.LocalRootSpan;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteHolder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteSource;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/** This is used to wrap Vert.x Handlers to provide nice user-friendly SERVER span names */
public final class RoutingContextHandlerWrapper implements Handler<RoutingContext> {

  private final Handler<RoutingContext> handler;

  public RoutingContextHandlerWrapper(Handler<RoutingContext> handler) {
    this.handler = handler;
  }

  @Override
  public void handle(RoutingContext context) {
    Context otelContext = Context.current();
    HttpRouteHolder.updateHttpRoute(
        otelContext, HttpRouteSource.CONTROLLER, RoutingContextHandlerWrapper::getRoute, context);

    try {
      handler.handle(context);
    } catch (Throwable throwable) {
      Span serverSpan = LocalRootSpan.fromContextOrNull(otelContext);
      if (serverSpan != null) {
        serverSpan.recordException(unwrapThrowable(throwable));
      }
      throw throwable;
    }
  }

  private static String getRoute(Context otelContext, RoutingContext routingContext) {
    return routingContext.currentRoute().getPath();
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
