/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.server.route;

import akka.http.scaladsl.server.RequestContext;
import akka.http.scaladsl.server.RouteResult;
import io.opentelemetry.context.Context;
import scala.Function1;
import scala.concurrent.Future;
import scala.runtime.AbstractFunction1;

public class AkkaRouteWrapper extends AbstractFunction1<RequestContext, Future<RouteResult>> {
  private final Function1<RequestContext, Future<RouteResult>> route;

  public AkkaRouteWrapper(Function1<RequestContext, Future<RouteResult>> route) {
    this.route = route;
  }

  @Override
  public Future<RouteResult> apply(RequestContext ctx) {
    Context context = Context.current();
    AkkaRouteHolder routeHolder = AkkaRouteHolder.get(context);
    if (routeHolder == null) {
      return route.apply(ctx);
    } else {
      routeHolder.save();
      return route
          .apply(ctx)
          .map(
              new AbstractFunction1<RouteResult, RouteResult>() {
                @Override
                public RouteResult apply(RouteResult result) {
                  if (result.getClass() == RouteResult.Rejected.class) {
                    routeHolder.restore();
                  }
                  return result;
                }
              },
              ctx.executionContext());
    }
  }
}
