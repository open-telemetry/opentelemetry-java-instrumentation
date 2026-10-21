/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.web.v3_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.LocalRootSpan;
import io.opentelemetry.instrumentation.api.internal.HttpServerExceptionRecorder;
import io.opentelemetry.instrumentation.api.internal.LoggerUtil;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource;
import io.opentelemetry.instrumentation.api.semconv.http.internal.HttpSchemaUrls;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import javax.annotation.Nullable;

/** This is used to wrap Vert.x Handlers to provide nice user-friendly SERVER span names */
public class RoutingContextHandlerWrapper implements Handler<RoutingContext> {

  private static final Logger logger =
      LoggerUtil.createLogger(
          GlobalOpenTelemetry.get(), "io.opentelemetry.vertx-web-3.0", HttpSchemaUrls.SCHEMA_URL);

  private final Handler<RoutingContext> handler;

  public RoutingContextHandlerWrapper(Handler<RoutingContext> handler) {
    this.handler = handler;
  }

  @Override
  public void handle(RoutingContext context) {
    Context otelContext = Context.current();
    // remember currently set route so it could be restored
    RoutingContextUtil.setRoute(context, RouteHolder.get(otelContext));
    String route = getRoute(otelContext, context);
    if (route != null && route.endsWith("/")) {
      route = route.substring(0, route.length() - 1);
    }
    HttpServerRoute.update(otelContext, HttpServerRouteSource.NESTED_CONTROLLER, route);

    Context routeContext = RouteHolder.init(otelContext, route);
    try (Scope ignored = routeContext.makeCurrent()) {
      handler.handle(context);
    } catch (Throwable t) {
      HttpServerExceptionRecorder.recordException(
          otelContext, LocalRootSpan.fromContextOrNull(otelContext), t, logger);
      throw t;
    }
  }

  @Nullable
  private static String getRoute(Context otelContext, RoutingContext routingContext) {
    String route = routingContext.currentRoute().getPath();
    if (route == null) {
      return RouteHolder.get(otelContext);
    }
    String mountPoint = routingContext.mountPoint();
    if (mountPoint != null) {
      return mountPoint.endsWith("/") && route.startsWith("/")
          ? mountPoint.substring(0, mountPoint.length() - 1) + route
          : mountPoint + route;
    }
    String existingRoute = RouteHolder.get(otelContext);
    return existingRoute != null ? existingRoute + route : route;
  }
}
