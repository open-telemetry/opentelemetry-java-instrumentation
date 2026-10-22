/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ziohttp.v3_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.internal.HttpRouteState;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource;
import scala.runtime.AbstractFunction1;
import zio.http.Handler;
import zio.http.RoutePattern;

public class HandlerWrapper {

  public static <IN, OUT, ERR> Handler<?, ERR, IN, OUT> wrap(
      Handler<?, ERR, IN, OUT> handler, RoutePattern<?> routePattern) {
    String s = routePattern.pathCodec().render();
    String route = s.isEmpty() ? "/" : s;

    return handler.contramap(
        new AbstractFunction1<IN, IN>() {

          @Override
          public IN apply(IN input) {
            updateRoute(route);
            return input;
          }
        });
  }

  private static void updateRoute(String route) {
    Context context = Context.current();
    HttpRouteState routeState = HttpRouteState.fromContextOrNull(context);
    HttpServerRouteSource source =
        routeState != null && routeState.getRoute() != null
            ? HttpServerRouteSource.NESTED_CONTROLLER
            : HttpServerRouteSource.CONTROLLER;
    HttpServerRoute.update(context, source, route);
  }

  private HandlerWrapper() {}
}
