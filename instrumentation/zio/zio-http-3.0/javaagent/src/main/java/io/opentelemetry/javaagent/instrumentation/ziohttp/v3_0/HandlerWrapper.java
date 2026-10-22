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
import scala.runtime.AbstractFunction1;
import scala.runtime.BoxedUnit;
import zio.Trace$;
import zio.http.Handler;
import zio.http.Request;
import zio.http.RoutePattern;

public class HandlerWrapper {

  private static final VirtualField<Request, Context> REQUEST_CONTEXT =
      VirtualField.find(Request.class, Context.class);

  public static <R, IN, OUT, ERR> Handler<R, ERR, IN, OUT> wrap(
      Handler<R, ERR, IN, OUT> handler, RoutePattern<?> routePattern) {
    String s = routePattern.pathCodec().render();
    String route = s.isEmpty() ? "/" : s;

    return new Handler.FromFunctionHandler<IN>(BoxedUnit.UNIT)
        .apply(
            new AbstractFunction1<IN, Handler<R, ERR, IN, OUT>>() {

              @Override
              public Handler<R, ERR, IN, OUT> apply(IN input) {
                Context context = contextFor(input);
                Handler<R, ERR, IN, OUT> result =
                    handler.map(
                        new AbstractFunction1<OUT, OUT>() {

                          @Override
                          public OUT apply(OUT output) {
                            updateRoute(context, route);
                            return output;
                          }
                        },
                        Trace$.MODULE$.empty());
                return result.mapError(
                    new AbstractFunction1<ERR, ERR>() {

                      @Override
                      public ERR apply(ERR error) {
                        updateRoute(context, route);
                        return error;
                      }
                    },
                    Trace$.MODULE$.empty());
              }
            });
  }

  private static Context contextFor(Object input) {
    Request request = findRequest(input);
    if (request == null) {
      return Context.current();
    }

    Context context = REQUEST_CONTEXT.get(request);
    if (context == null) {
      context = Context.current();
      REQUEST_CONTEXT.set(request, context);
    }
    return context;
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

  private HandlerWrapper() {}
}
