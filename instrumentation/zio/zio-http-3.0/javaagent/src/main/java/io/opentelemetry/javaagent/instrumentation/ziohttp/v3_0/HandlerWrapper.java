/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ziohttp.v3_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import javax.annotation.Nullable;
import scala.Product;
import scala.runtime.AbstractFunction0;
import scala.runtime.AbstractFunction1;
import scala.runtime.BoxedUnit;
import scala.runtime.Nothing$;
import zio.Trace$;
import zio.ZIO;
import zio.ZIO$;
import zio.http.Handler;
import zio.http.Request;
import zio.http.RoutePattern;

public class HandlerWrapper {

  private static final VirtualField<Request, RouteContext> REQUEST_CONTEXT =
      VirtualField.find(Request.class, RouteContext.class);

  public static <R, IN, OUT, ERR> Handler<R, ERR, IN, OUT> wrap(
      Handler<R, ERR, IN, OUT> handler, RoutePattern<?> routePattern) {
    String s = routePattern.pathCodec().render();
    String route = s.isEmpty() ? "/" : s;

    return new Handler.FromFunctionZIO<IN>(BoxedUnit.UNIT)
        .apply(
            new AbstractFunction1<IN, ZIO<R, ERR, OUT>>() {

              @Override
              public ZIO<R, ERR, OUT> apply(IN input) {
                RouteContext routeContext = contextFor(input);
                Handler<R, ERR, IN, OUT> result =
                    handler
                        .map(
                            new AbstractFunction1<OUT, OUT>() {

                              @Override
                              public OUT apply(OUT output) {
                                updateRoute(routeContext.context, route);
                                return output;
                              }
                            },
                            Trace$.MODULE$.empty())
                        .mapError(
                            new AbstractFunction1<ERR, ERR>() {

                              @Override
                              public ERR apply(ERR error) {
                                updateRoute(routeContext.context, route);
                                return error;
                              }
                            },
                            Trace$.MODULE$.empty());
                ZIO<R, ERR, OUT> effect = result.apply(input);
                Request request = routeContext.request;
                if (request == null) {
                  return effect;
                }
                return effect.ensuring(
                    new AbstractFunction0<ZIO<R, Nothing$, Object>>() {
                      @Override
                      public ZIO<R, Nothing$, Object> apply() {
                        return releaseEffect(request, routeContext);
                      }
                    },
                    Trace$.MODULE$.empty());
              }
            });
  }

  private static RouteContext contextFor(Object input) {
    Request request = findRequest(input);
    if (request == null) {
      return new RouteContext(null, Context.current());
    }

    Context context = Context.current();
    synchronized (request) {
      RouteContext routeContext = REQUEST_CONTEXT.get(request);
      if (routeContext == null) {
        routeContext = new RouteContext(request, context);
        REQUEST_CONTEXT.set(request, routeContext);
      } else {
        routeContext.activeHandlers++;
      }
      return routeContext;
    }
  }

  private static void release(Request request, RouteContext routeContext) {
    synchronized (request) {
      if (--routeContext.activeHandlers == 0 && REQUEST_CONTEXT.get(request) == routeContext) {
        REQUEST_CONTEXT.set(request, null);
      }
    }
  }

  // ZIO is contravariant in its environment, so this no-environment effect is safe for any R.
  @SuppressWarnings("unchecked")
  private static <R> ZIO<R, Nothing$, Object> releaseEffect(
      Request request, RouteContext routeContext) {
    return (ZIO<R, Nothing$, Object>)
        (ZIO<?, ?, ?>)
            ZIO$.MODULE$.succeed(
                new AbstractFunction0<Object>() {
                  @Override
                  public Object apply() {
                    release(request, routeContext);
                    return BoxedUnit.UNIT;
                  }
                },
                Trace$.MODULE$.empty());
  }

  @Nullable
  private static Request findRequest(Object input) {
    if (input instanceof Request) {
      return (Request) input;
    }
    if (input instanceof Product) {
      Product product = (Product) input;
      for (int i = 0; i < product.productArity(); i++) {
        Request request = findRequest(product.productElement(i));
        if (request != null) {
          return request;
        }
      }
    }
    return null;
  }

  private static void updateRoute(Context context, String route) {
    HttpServerRoute.update(context, HttpServerRouteSource.CONTROLLER, route);
  }

  private static class RouteContext {
    @Nullable private final Request request;
    private final Context context;
    private int activeHandlers = 1;

    private RouteContext(@Nullable Request request, Context context) {
      this.request = request;
      this.context = context;
    }
  }

  private HandlerWrapper() {}
}
