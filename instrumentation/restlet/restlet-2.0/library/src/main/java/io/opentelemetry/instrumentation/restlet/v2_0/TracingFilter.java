/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v2_0;

import static io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource.CONTROLLER;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.routing.Filter;

final class TracingFilter extends Filter {

  private final Instrumenter<Request, Response> instrumenter;
  private final String path;

  TracingFilter(Instrumenter<Request, Response> instrumenter, String path) {
    this.instrumenter = instrumenter;
    this.path = path;
  }

  @Override
  public int doHandle(Request request, Response response) {
    Context parentContext = Context.current();
    if (!instrumenter.shouldStart(parentContext, request)) {
      super.doHandle(request, response);
      return CONTINUE;
    }

    Throwable error = null;
    Context context = instrumenter.start(parentContext, request);
    try (Scope ignored = context.makeCurrent()) {
      HttpServerRoute.update(context, CONTROLLER, path);
      super.doHandle(request, response);
    } catch (Throwable t) {
      error = t;
      throw t;
    } finally {
      if (response.getStatus() != null && response.getStatus().isError()) {
        error = response.getStatus().getThrowable();
      }
      instrumenter.end(context, request, response, error);
    }

    return CONTINUE;
  }
}
