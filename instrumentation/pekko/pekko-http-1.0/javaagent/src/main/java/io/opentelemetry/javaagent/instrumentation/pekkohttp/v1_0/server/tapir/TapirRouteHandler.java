/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server.tapir;

import io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server.route.PekkoRouteHolder;
import java.nio.charset.Charset;
import org.apache.pekko.http.scaladsl.model.Uri;
import org.apache.pekko.http.scaladsl.server.RequestContext;
import scala.Option;
import scala.concurrent.Future;
import sttp.monad.MonadError;
import sttp.tapir.Endpoint;
import sttp.tapir.model.ServerRequest;
import sttp.tapir.server.interceptor.DecodeFailureContext;
import sttp.tapir.server.interceptor.DecodeSuccessContext;
import sttp.tapir.server.interceptor.EndpointHandler;
import sttp.tapir.server.interceptor.EndpointInterceptor;
import sttp.tapir.server.interceptor.Responder;
import sttp.tapir.server.interceptor.SecurityFailureContext;
import sttp.tapir.server.interpreter.BodyListener;

public final class TapirRouteHandler<T, B> implements EndpointHandler<Future<T>, B> {
  private static final Uri.Path EMPTY = Uri.Path$.MODULE$.apply("", Charset.defaultCharset());
  private final EndpointHandler<Future<T>, B> endpointHandler;

  public static <T> EndpointInterceptor<Future<T>> interceptor() {
    return new EndpointInterceptor<Future<T>>() {
      @Override
      public <B> EndpointHandler<Future<T>, B> apply(Responder<Future<T>, B> responder,
          EndpointHandler<Future<T>, B> endpointHandler) {
        return new TapirRouteHandler<>(endpointHandler);
      }
    };
  }
  
  private TapirRouteHandler(EndpointHandler<Future<T>, B> endpointHandler) {
    this.endpointHandler = endpointHandler;
  }

  @Override
  public <A, U, I> Future<T> onDecodeSuccess(DecodeSuccessContext<Future<T>, A, U, I> ctx,
      MonadError<Future<T>> monad, BodyListener<Future<T>, B> bodyListener) {
    updateSpan(ctx.endpoint(), ctx.request());
    return endpointHandler.onDecodeSuccess(ctx, monad, bodyListener);
  }

  @Override
  public <A> Future<T> onSecurityFailure(SecurityFailureContext<Future<T>, A> ctx,
      MonadError<Future<T>> monad, BodyListener<Future<T>, B> bodyListener) {
    updateSpan(ctx.endpoint(), ctx.request());
    return endpointHandler.onSecurityFailure(ctx, monad, bodyListener);
  }

  @Override
  public Future<T> onDecodeFailure(DecodeFailureContext ctx, MonadError<Future<T>> monad,
      BodyListener<Future<T>, B> bodyListener) {
    updateSpan(ctx.endpoint(), ctx.request());
    return endpointHandler.onDecodeFailure(ctx, monad, bodyListener);
  }

  private static void updateSpan(Endpoint<?, ?, ?, ?, ?> endpoint, ServerRequest request) {
    Object underlyingRequest = request.underlying();
    if (underlyingRequest instanceof RequestContext) {
      RequestContext pekkoCtx = (RequestContext) underlyingRequest;
      pekkoCtx.request()
          .getAttribute(PekkoRouteHolder.ATTRIBUTE_KEY)
          .ifPresent(routeHolder -> {
            String path =
                endpoint.showPathTemplate(
                    (index, pc) ->
                        pc.name().isDefined() ? "{" + pc.name().get() + "}"
                            : "{param" + index + "}",
                    Option.empty(),
                    false,
                    "*",
                    Option.apply("*"),
                    Option.apply("*"));
            routeHolder.push(pekkoCtx.unmatchedPath(), EMPTY, path);
          });
    }
  }
}
