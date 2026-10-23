/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ziohttp.v3_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource;
import java.util.Optional;
import scala.Option;
import scala.runtime.AbstractFunction1;
import scala.runtime.AbstractFunction2;
import scala.runtime.BoxedUnit;
import scala.runtime.Nothing$;
import zio.Cause;
import zio.Exit;
import zio.FiberRef;
import zio.FiberRef.unsafe$;
import zio.Trace$;
import zio.Unsafe;
import zio.Unsafe$;
import zio.ZIO;
import zio.ZIO$;
import zio.http.Handler;
import zio.http.RoutePattern;

public class HandlerWrapper {

  private static final FiberRef<Optional<Context>> ROUTE_CONTEXT =
      unsafe$.MODULE$.make(
          Optional.empty(),
          new AbstractFunction1<Optional<Context>, Optional<Context>>() {
            @Override
            public Optional<Context> apply(Optional<Context> context) {
              return context;
            }
          },
          new AbstractFunction2<Optional<Context>, Optional<Context>, Optional<Context>>() {
            @Override
            public Optional<Context> apply(
                Optional<Context> parentContext, Optional<Context> childContext) {
              return childContext;
            }
          },
          Unsafe$.MODULE$.unsafe());

  public static <R, IN, OUT, ERR> Handler<R, ERR, IN, OUT> wrap(
      Handler<R, ERR, IN, OUT> handler, RoutePattern<?> routePattern) {
    String s = routePattern.pathCodec().render();
    String route = s.isEmpty() ? "/" : s;

    return new Handler.FromFunctionZIO<IN>(BoxedUnit.UNIT)
        .apply(
            new AbstractFunction1<IN, ZIO<R, ERR, OUT>>() {

              @Override
              public ZIO<R, ERR, OUT> apply(IN input) {
                Context context = Context.current();
                ZIO<R, ERR, OUT> effect = handler.apply(input);
                return withRouteContext(context, effect, route);
              }
            });
  }

  private static <R, ERR, OUT> ZIO<R, ERR, OUT> withRouteContext(
      Context context, ZIO<R, ERR, OUT> effect, String route) {
    return ROUTE_CONTEXT
        .get(Trace$.MODULE$.empty())
        .flatMap(
            new AbstractFunction1<Optional<Context>, ZIO<R, ERR, OUT>>() {
              @Override
              public ZIO<R, ERR, OUT> apply(Optional<Context> currentContext) {
                Context routeContext = currentContext.orElse(context);
                ZIO<R, ERR, OUT> result =
                    effect.onExit(
                        new AbstractFunction1<Exit<ERR, OUT>, ZIO<R, Nothing$, Object>>() {
                          @Override
                          public ZIO<R, Nothing$, Object> apply(Exit<ERR, OUT> exit) {
                            return updateRouteEffect(routeContext, route, exit);
                          }
                        },
                        Trace$.MODULE$.empty());
                return ROUTE_CONTEXT.locally(
                    Optional.of(routeContext), result, Trace$.MODULE$.empty());
              }
            },
            Trace$.MODULE$.empty());
  }

  // This no-environment effect is safe for any R.
  @SuppressWarnings("unchecked")
  private static <R> ZIO<R, Nothing$, Object> updateRouteEffect(
      Context context, String route, Exit<?, ?> exit) {
    return (ZIO<R, Nothing$, Object>)
        (ZIO<?, ?, ?>)
            ZIO$.MODULE$.succeedUnsafe(
                new AbstractFunction1<Unsafe, Object>() {
                  @Override
                  public Object apply(Unsafe unsafe) {
                    Option<?> cause = exit.causeOption();
                    if (exit.isSuccess()
                        || (cause.isDefined() && ((Cause<?>) cause.get()).isFailure())) {
                      HttpServerRoute.update(context, HttpServerRouteSource.CONTROLLER, route);
                    }
                    return BoxedUnit.UNIT;
                  }
                },
                Trace$.MODULE$.empty());
  }

  private HandlerWrapper() {}
}
