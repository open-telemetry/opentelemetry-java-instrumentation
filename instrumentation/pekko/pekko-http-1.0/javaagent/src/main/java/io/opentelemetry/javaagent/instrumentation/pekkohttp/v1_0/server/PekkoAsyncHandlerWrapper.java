/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

import static io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server.PekkoHttpServerSingletons.errorResponse;
import static io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server.PekkoHttpServerSingletons.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource;
import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseCustomizerHolder;
import io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server.route.PekkoRouteHolder;
import java.util.List;
import org.apache.pekko.http.javadsl.model.HttpHeader;
import org.apache.pekko.http.scaladsl.model.HttpRequest;
import org.apache.pekko.http.scaladsl.model.HttpResponse;
import scala.Function1;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.concurrent.impl.Promise;
import scala.runtime.AbstractFunction1;
import scala.util.Try;

public final class PekkoAsyncHandlerWrapper
    implements Function1<HttpRequest, Future<HttpResponse>> {

  private final Function1<HttpRequest, Future<HttpResponse>> handler;
  private final ExecutionContext executionContext;

  private PekkoAsyncHandlerWrapper(
      Function1<HttpRequest, Future<HttpResponse>> handler, ExecutionContext executionContext) {
    this.handler = handler;
    this.executionContext = executionContext;
  }

  public static Function1<HttpRequest, Future<HttpResponse>> wrap(
      Function1<HttpRequest, Future<HttpResponse>> handler, ExecutionContext executionContext) {
    if (handler instanceof PekkoAsyncHandlerWrapper) {
      return handler;
    }
    return new PekkoAsyncHandlerWrapper(handler, executionContext);
  }

  @Override
  public Future<HttpResponse> apply(HttpRequest request) {
    PekkoTracingRequest tracingRequest =
        request.getAttribute(PekkoTracingRequest.ATTR_KEY).orElse(PekkoTracingRequest.EMPTY);

    // HTTP/1.1 branch used while HTTP/2 preview is enabled.
    // PekkoHttpServerTracer has already created the server span.
    if (tracingRequest != PekkoTracingRequest.EMPTY) {
      HttpRequest applicationRequest =
          (HttpRequest) request.removeAttribute(PekkoTracingRequest.ATTR_KEY);

      try (Scope ignored = tracingRequest.context.makeCurrent()) {
        return handler.apply(applicationRequest);
      }
    }

    // A missing tracing request can also mean that the HTTP/1.1 tracer
    // decided not to start a span. Only own the server span lifecycle
    // here for an actual HTTP/2 request.
    if (!request.protocol().value().startsWith("HTTP/2")) {
      return handler.apply(request);
    }

    // True HTTP/2 bypasses PekkoHttpServerTracer, so create and own
    // the server span lifecycle here.
    Context parentContext = Context.current();
    if (!instrumenter().shouldStart(parentContext, request)) {
      return handler.apply(request);
    }

    PekkoRouteHolder routeHolder = PekkoRouteHolder.create();
    Context context = instrumenter().start(parentContext, request).with(routeHolder);

    HttpRequest applicationRequest =
        (HttpRequest) request.addAttribute(PekkoRouteHolder.ATTRIBUTE_KEY, routeHolder);

    Future<HttpResponse> future;
    try (Scope ignored = context.makeCurrent()) {
      future = handler.apply(applicationRequest);
    } catch (RuntimeException | Error exception) {
      instrumenter().end(context, request, errorResponse(), exception);
      throw exception;
    }

    Promise.DefaultPromise<HttpResponse> promise = new Promise.DefaultPromise<>();

    future.onComplete(
        new AbstractFunction1<Try<HttpResponse>, Object>() {
          @Override
          public Object apply(Try<HttpResponse> result) {
            try (Scope ignored = context.makeCurrent()) {
              if (result.isFailure()) {
                Throwable exception = result.failed().get();
                instrumenter().end(context, request, errorResponse(), exception);
                return promise.complete(result);
              }

              HttpResponse response = result.get();

              PekkoHttpResponseMutator responseMutator = new PekkoHttpResponseMutator();
              HttpServerResponseCustomizerHolder.getCustomizer()
                  .customize(context, response, responseMutator);

              List<HttpHeader> headers = responseMutator.getHeaders();
              if (!headers.isEmpty()) {
                response = (HttpResponse) response.addHeaders(headers);
              }

              PekkoRouteHolder responseRouteHolder =
                  response.getAttribute(PekkoRouteHolder.ATTRIBUTE_KEY).orElse(routeHolder);

              HttpServerRoute.update(
                  context, HttpServerRouteSource.CONTROLLER, responseRouteHolder.route());

              instrumenter().end(context, request, response, null);

              return promise.success(response);
            }
          }
        },
        executionContext);

    return promise;
  }
}
