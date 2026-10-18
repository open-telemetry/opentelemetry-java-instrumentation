/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.armeria.v1_3;

import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.server.HttpResponseException;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.SimpleDecoratingHttpService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.ErrorCauseExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.LocalRootSpan;
import io.opentelemetry.instrumentation.api.internal.ExceptionRecorder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource;
import io.opentelemetry.semconv.SchemaUrls;

class ServerDecorator extends SimpleDecoratingHttpService {
  private static final Logger logger =
      ExceptionRecorder.createLogger(
          GlobalOpenTelemetry.get(), "io.opentelemetry.armeria-1.3", SchemaUrls.V1_41_0);

  private final HttpService libraryDelegate;

  ServerDecorator(HttpService delegate, HttpService libraryDelegate) {
    super(delegate);
    this.libraryDelegate = libraryDelegate;
  }

  @Override
  public HttpResponse serve(ServiceRequestContext ctx, HttpRequest req) throws Exception {
    // If there is no server span fall back to armeria library instrumentation. Server span is
    // usually created by netty instrumentation, it can be missing when netty instrumentation is
    // disabled or when http2 is used (netty instrumentation does not support http2).
    if (!LocalRootSpan.current().getSpanContext().isValid()) {
      return libraryDelegate.serve(ctx, req);
    }

    String matchedRoute = ctx.config().route().patternString();
    if (matchedRoute == null || matchedRoute.isEmpty()) {
      matchedRoute = "/";
    } else if (!matchedRoute.startsWith("/")) {
      matchedRoute = "/" + matchedRoute;
    }

    Context otelContext = Context.current();

    HttpServerRoute.update(otelContext, HttpServerRouteSource.SERVER, matchedRoute);

    try {
      return unwrap().serve(ctx, req);
    } catch (Throwable t) {
      Throwable error = ErrorCauseExtractor.getDefault().extract(t);
      // Armeria uses this exception to return a response, including successful responses.
      if (error instanceof HttpResponseException) {
        if (error.getCause() == null) {
          throw t;
        }
        error = ErrorCauseExtractor.getDefault().extract(error.getCause());
      }
      Span span = Span.fromContext(otelContext);
      span.setStatus(StatusCode.ERROR);
      ExceptionRecorder.recordServerException(otelContext, span, error, logger);

      throw t;
    }
  }
}
