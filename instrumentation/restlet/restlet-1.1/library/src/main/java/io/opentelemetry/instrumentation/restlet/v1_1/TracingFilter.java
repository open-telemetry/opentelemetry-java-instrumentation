/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v1_1;

import static io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource.CONTROLLER;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import org.restlet.Filter;
import org.restlet.data.Request;
import org.restlet.data.Response;

final class TracingFilter extends Filter {

  private final Instrumenter<Request, Response> instrumenter;
  private final String path;

  public TracingFilter(Instrumenter<Request, Response> instrumenter, String path) {
    this.instrumenter = instrumenter;
    this.path = path;
  }

  @Override
  public int doHandle(Request request, Response response) {

    Context parentContext = Context.current();
    Context context = parentContext;

    Scope scope = null;

    if (instrumenter.shouldStart(parentContext, request)) {
      context = instrumenter.start(parentContext, request);
      scope = context.makeCurrent();
    }

    HttpServerRoute.update(context, CONTROLLER, path);

    Throwable statusThrowable = null;
    try {
      super.doHandle(request, response);
    } catch (Throwable t) {
      statusThrowable = t;
    }

    if (scope == null) {
      return CONTINUE;
    }

    scope.close();

    if (response.getStatus() != null && response.getStatus().isError()) {
      statusThrowable = response.getStatus().getThrowable();
    }

    instrumenter.end(context, request, response, statusThrowable);

    return CONTINUE;
  }
}
