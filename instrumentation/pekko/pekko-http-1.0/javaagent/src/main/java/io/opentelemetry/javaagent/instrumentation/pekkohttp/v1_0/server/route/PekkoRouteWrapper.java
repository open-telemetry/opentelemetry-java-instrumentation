/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server.route;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import org.apache.pekko.http.scaladsl.server.RequestContext;
import org.apache.pekko.http.scaladsl.server.RouteResult;
import scala.Function1;
import scala.concurrent.Future;

public class PekkoRouteWrapper implements Function1<RequestContext, Future<RouteResult>> {
  private final Function1<RequestContext, Future<RouteResult>> route;

  public PekkoRouteWrapper(Function1<RequestContext, Future<RouteResult>> route) {
    this.route = route;
  }

  @Override
  public Future<RouteResult> apply(RequestContext ctx) {
    Context context = Java8BytecodeBridge.currentContext();
    PekkoRouteHolder routeHolder = PekkoRouteHolder.get(context);
    if (routeHolder == null) {
      return route.apply(ctx);
    } else {
      routeHolder.save();
      return route
          .apply(ctx)
          .map(
              result -> {
                if (result.getClass() == RouteResult.Rejected.class) {
                  routeHolder.restore();
                }
                return result;
              },
              ctx.executionContext());
    }
  }
}
