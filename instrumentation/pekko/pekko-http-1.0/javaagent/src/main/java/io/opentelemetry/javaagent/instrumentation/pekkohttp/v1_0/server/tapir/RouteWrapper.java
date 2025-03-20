/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server.tapir;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server.route.PekkoRouteHolder;
import java.nio.charset.Charset;
import org.apache.pekko.http.scaladsl.model.Uri;
import org.apache.pekko.http.scaladsl.server.RequestContext;
import org.apache.pekko.http.scaladsl.server.RouteResult;
import scala.Function1;
import scala.Function2;
import scala.Option;
import scala.PartialFunction;
import scala.Unit;
import scala.concurrent.Future;
import scala.util.Try;
import sttp.tapir.EndpointInput;
import sttp.tapir.server.ServerEndpoint;

public class RouteWrapper implements Function1<RequestContext, Future<RouteResult>> {
  private static final Uri.Path EMPTY = Uri.Path$.MODULE$.apply("", Charset.defaultCharset());
  private final Function1<RequestContext, Future<RouteResult>> route;
  private final ServerEndpoint<?, ?> serverEndpoint;

  public RouteWrapper(
      ServerEndpoint<?, ?> serverEndpoint, Function1<RequestContext, Future<RouteResult>> route) {
    this.route = route;
    this.serverEndpoint = serverEndpoint;
  }

  class Finalizer implements PartialFunction<Try<RouteResult>, Unit> {
    private final Uri.Path beforeMatch;

    public Finalizer(Uri.Path beforeMatch) {
      this.beforeMatch = beforeMatch;
    }

    @Override
    public boolean isDefinedAt(Try<RouteResult> tryResult) {
      return tryResult.isSuccess();
    }

    @Override
    public Unit apply(Try<RouteResult> tryResult) {
      Context context = Java8BytecodeBridge.currentContext();
      PekkoRouteHolder routeHolder = PekkoRouteHolder.get(context);
      if (routeHolder != null && tryResult.isSuccess()) {
        RouteResult result = tryResult.get();
        if (result.getClass() == RouteResult.Complete.class) {
          String path =
              serverEndpoint.showPathTemplate(
                  (index, pc) ->
                      pc.name().isDefined() ? "{" + pc.name().get() + "}" : "{param" + index + "}",
                  Option.apply(
                      (Function2<Object, EndpointInput.Query<?>, String>)
                          (index, q) -> q.name() + "={" + q.name() + "}"),
                  false,
                  "*",
                  Option.apply("*"),
                  Option.apply("*"));
          routeHolder.push(beforeMatch, EMPTY, path);
        }
      }
      return null;
    }
  }

  @Override
  public Future<RouteResult> apply(RequestContext ctx) {
    return route.apply(ctx).andThen(new Finalizer(ctx.unmatchedPath()), ctx.executionContext());
  }
}
