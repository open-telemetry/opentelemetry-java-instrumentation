/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ziohttp.v3_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource;
import scala.runtime.AbstractFunction1;
import zio.http.Handler;
import zio.http.RoutePattern;

public final class HandlerWrapper {

  public static <IN, OUT, ERR> Handler<?, ERR, IN, OUT> wrap(
      Handler<?, ERR, IN, OUT> handler, RoutePattern<?> routePattern) {
    String s = routePattern.pathCodec().render();
    String route = s.isEmpty() ? "/" : s;

    Handler<?, ERR, IN, OUT> result =
        handler.map(
            new AbstractFunction1<OUT, OUT>() {

              @Override
              public OUT apply(OUT out) {
                updateRoute(route);
                return out;
              }
            },
            null);
    result =
        result.mapError(
            new AbstractFunction1<ERR, ERR>() {

              @Override
              public ERR apply(ERR error) {
                updateRoute(route);
                return error;
              }
            },
            null);

    return result;
  }

  private static void updateRoute(String route) {
    HttpServerRoute.update(Context.current(), HttpServerRouteSource.CONTROLLER, route);
  }

  private HandlerWrapper() {}
}
