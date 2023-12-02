/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.armeria.v1_3;

import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.SimpleDecoratingHttpService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.ErrorCauseExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.LocalRootSpan;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource;

class ServerDecorator extends SimpleDecoratingHttpService {
  private final HttpService libraryDelegate;

  ServerDecorator(HttpService delegate, HttpService libraryDelegate) {
    super(delegate);
    this.libraryDelegate = libraryDelegate;
  }

  @Override
  public HttpResponse serve(ServiceRequestContext ctx, HttpRequest req) throws Exception {
    // If there is no server span fall back to armeria liberary instrumentation. Server span is
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
    } catch (Throwable throwable) {
      Span span = Span.fromContext(otelContext);
      span.setStatus(StatusCode.ERROR);
      span.recordException(ErrorCauseExtractor.getDefault().extract(throwable));

      throw throwable;
    }
  }
}
