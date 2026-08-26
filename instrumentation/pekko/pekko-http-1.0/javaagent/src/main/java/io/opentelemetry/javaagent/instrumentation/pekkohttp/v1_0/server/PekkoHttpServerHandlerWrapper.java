/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

import static io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server.PekkoHttpServerSingletons.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server.route.PekkoRouteHolder;
import org.apache.pekko.http.impl.engine.http2.Http2$;
import org.apache.pekko.http.scaladsl.model.HttpRequest;
import org.apache.pekko.http.scaladsl.model.HttpResponse;
import scala.Function1;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.runtime.AbstractFunction1;
import scala.util.Success;
import scala.util.Try;

/**
 * Wraps the handler function that is passed to {@code Http2Ext.bindAndHandleAsync}.
 *
 * <p>With http/1.1 requests and responses are matched by their order in the connection flow, see
 * {@link PekkoHttpServerTracer}. Http/2 multiplexes concurrent streams on a single connection, so
 * responses can arrive in any order. Wrapping the handler function pairs each request with the
 * future that completes with the response for that request.
 */
public class PekkoHttpServerHandlerWrapper
    extends AbstractFunction1<HttpRequest, Future<HttpResponse>> {

  private final Function1<HttpRequest, Future<HttpResponse>> handler;
  private final ExecutionContext executionContext;

  public static Function1<HttpRequest, Future<HttpResponse>> wrap(
      Function1<HttpRequest, Future<HttpResponse>> handler, ExecutionContext executionContext) {
    if (handler instanceof PekkoHttpServerHandlerWrapper) {
      return handler;
    }
    return new PekkoHttpServerHandlerWrapper(handler, executionContext);
  }

  private PekkoHttpServerHandlerWrapper(
      Function1<HttpRequest, Future<HttpResponse>> handler, ExecutionContext executionContext) {
    this.handler = handler;
    this.executionContext = executionContext;
  }

  @Override
  public Future<HttpResponse> apply(HttpRequest request) {
    HttpRequest serverRequest = request;
    PekkoTracingRequest tracedRequest =
        serverRequest.getAttribute(PekkoTracingRequest.ATTR_KEY).orElse(null);
    if (tracedRequest != null) {
      // remove the attribute before the request is passed to user code
      serverRequest = (HttpRequest) serverRequest.removeAttribute(PekkoTracingRequest.ATTR_KEY);
      if (!isHttp2(serverRequest)) {
        // request was received over http/1.1, its span is started and ended by
        // PekkoHttpServerTracer that is attached to the http/1.1 server blueprint, here we only
        // need to make the context available to user code
        try (Scope ignored = tracedRequest.context.makeCurrent()) {
          return handler.apply(serverRequest);
        }
      }
      // request was upgraded from http/1.1 to http/2 and replayed through the http/2 stack, the
      // span that PekkoHttpServerTracer started for it belongs to the upgrade request, start a new
      // span for the replayed request. Only clients that send the request that is to be served as
      // the upgrade request get here, clients that negotiate the upgrade with a separate request
      // arrive with no attribute at all.
    }

    Context parentContext = Context.current();
    if (!instrumenter().shouldStart(parentContext, serverRequest)) {
      return handler.apply(serverRequest);
    }

    PekkoRouteHolder routeHolder = PekkoRouteHolder.create();
    Context context = instrumenter().start(parentContext, serverRequest).with(routeHolder);
    PekkoTracingRequest tracingRequest =
        new PekkoTracingRequest(context, serverRequest, routeHolder);
    HttpRequest applicationRequest =
        (HttpRequest) serverRequest.addAttribute(PekkoRouteHolder.ATTRIBUTE_KEY, routeHolder);

    Future<HttpResponse> responseFuture;
    try (Scope ignored = context.makeCurrent()) {
      responseFuture = handler.apply(applicationRequest);
    } catch (Throwable t) {
      PekkoHttpServerSingletons.endSpanWithError(tracingRequest, t);
      // rethrowing without any wrapping to avoid any change to the underlying application behavior
      throw sneakyThrow(t);
    }
    if (responseFuture == null) {
      PekkoHttpServerSingletons.endSpanWithError(tracingRequest, null);
      return null;
    }

    return responseFuture.transform(new EndSpanHandler(tracingRequest), executionContext);
  }

  private static boolean isHttp2(HttpRequest request) {
    return request.getAttribute(Http2$.MODULE$.streamId()).isPresent();
  }

  @SuppressWarnings({"TypeParameterUnusedInFormals", "unchecked"}) // fine
  private static <T extends Throwable> T sneakyThrow(Throwable t) throws T {
    throw (T) t;
  }

  private static class EndSpanHandler
      extends AbstractFunction1<Try<HttpResponse>, Try<HttpResponse>> {
    private final PekkoTracingRequest tracingRequest;

    EndSpanHandler(PekkoTracingRequest tracingRequest) {
      this.tracingRequest = tracingRequest;
    }

    @Override
    public Try<HttpResponse> apply(Try<HttpResponse> result) {
      if (result.isSuccess()) {
        return new Success<>(PekkoHttpServerSingletons.endSpan(tracingRequest, result.get()));
      }
      PekkoHttpServerSingletons.endSpanWithError(tracingRequest, result.failed().get());
      return result;
    }
  }
}
