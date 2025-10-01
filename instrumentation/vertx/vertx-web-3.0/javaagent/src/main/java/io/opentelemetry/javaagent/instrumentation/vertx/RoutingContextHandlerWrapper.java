/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.LocalRootSpan;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * Wraps Vert.x {@link RoutingContext} handlers to ensure proper propagation of the OpenTelemetry
 * context and provide nice user-friendly SERVER span names.
 */
public final class RoutingContextHandlerWrapper implements Handler<RoutingContext> {

  private final Handler<RoutingContext> handler;
  private final Context parentContext; // ✅ capture once when wrapper is created

  public RoutingContextHandlerWrapper(Handler<RoutingContext> handler) {
    this.handler = handler;
    this.parentContext = Context.current(); // ✅ save the active context at registration time
  }

  @Override
  public void handle(RoutingContext context) {
//    try (Scope scope = parentContext.makeCurrent()) {
      // restore any route information stored previously
      RoutingContextUtil.setRoute(context, RouteHolder.get(parentContext));
      String route = getRoute(parentContext, context);
      if (route != null && route.endsWith("/")) {
        route = route.substring(0, route.length() - 1);
      }
      HttpServerRoute.update(parentContext, HttpServerRouteSource.NESTED_CONTROLLER, route);
    try (Scope ignore = RouteHolder.init(parentContext, route).makeCurrent()) {
      handler.handle(context);

    } catch (Throwable throwable) {
      Span serverSpan = LocalRootSpan.fromContextOrNull(parentContext);
      if (serverSpan != null) {
        serverSpan.recordException(unwrapThrowable(throwable));
      }
      throw throwable;
    }
  }

  private static String getRoute(Context otelContext, RoutingContext routingContext) {
    String route = routingContext.currentRoute().getPath();
    String existingRoute = RouteHolder.get(otelContext);
    return existingRoute != null ? existingRoute + route : route;
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
