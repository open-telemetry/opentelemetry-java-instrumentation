/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server.route;

import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import org.apache.pekko.http.scaladsl.model.HttpRequest;
import org.apache.pekko.http.scaladsl.model.HttpResponse;
import org.apache.pekko.http.scaladsl.server.RequestContext;
import org.apache.pekko.http.scaladsl.server.RouteResult;
import scala.Function1;
import scala.concurrent.Future;
import scala.util.Success;

public class PekkoRouteWrapper implements Function1<RequestContext, Future<RouteResult>> {
  private final Function1<RequestContext, Future<RouteResult>> route;

  public PekkoRouteWrapper(Function1<RequestContext, Future<RouteResult>> route) {
    this.route = route;
  }

  @Override
  public Future<RouteResult> apply(RequestContext ctx) {
    PekkoRouteHolder parentRouteHolder = ctx.request().getAttribute(PekkoRouteHolder.ATTRIBUTE_KEY)
        .orElse(null);
    if (parentRouteHolder == null) {
      return route.apply(ctx);
    } else {
      PekkoRouteHolder childRouteHolder = parentRouteHolder.createChild();
      // Propagate the child RouteHolder via pekko httprequest attribute, which ensures
      // sibling directives share the same parentRouteHolder
      RequestContext requestWithChildRouteHolder = ctx.mapRequest(req ->
          (HttpRequest) req.addAttribute(PekkoRouteHolder.ATTRIBUTE_KEY, childRouteHolder));
      // RouteHolder needs to be in otel context for PathMatcherStaticInstrumentation
      try (Scope scope = Java8BytecodeBridge.currentContext().with(childRouteHolder).makeCurrent()) {
        return route
            .apply(requestWithChildRouteHolder)
            .transform(tryResult -> {
              // Add the route holder to the pekko httpresponse as an attribute if:
              // - it successfully returned a response
              // - the route holder can generate a route
              // - there isn't already a route holder in the response, which will have come
              //   from deeper in the Directive stack so will be more specific
              if (tryResult.isSuccess() && childRouteHolder.hasRoute()) {
                RouteResult result = tryResult.get();
                if (result.getClass() == RouteResult.Complete.class) {
                  HttpResponse response = ((RouteResult.Complete) result).getResponse();
                  if (!response.getAttribute(PekkoRouteHolder.ATTRIBUTE_KEY).isPresent()) {
                    HttpResponse newResponse = (HttpResponse) response.addAttribute(
                        PekkoRouteHolder.ATTRIBUTE_KEY, childRouteHolder);
                    return new Success<>(new RouteResult.Complete(newResponse));
                  }
                }
              }
              if (tryResult.isFailure()) {
                // The route threw an exception, but was likely a match. Set this as the
                // "override" for the parent routeHolder, meaning that when calling
                // parentRouteHolder.route() it will actually call childRouteHolder.route().
                // A parent routeHolder will eventually be added to the httpresponse attribute after
                // bubbling up the Directive stack to a pekko exception handler, which converts the
                // error to a successful httpresponse.
                // NB. If sibling Directives get here, the last one wins.
                if (childRouteHolder.hasRoute()) {
                  parentRouteHolder.setOverride(childRouteHolder);
                }
              }
              return tryResult;
            }, ctx.executionContext());
      }
    }
  }
}
