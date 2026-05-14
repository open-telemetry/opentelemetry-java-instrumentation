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
      // Restlet StatusFilter can swallow downstream exceptions and attach them to the response
      // status, so only use the status throwable when no exception escaped directly.
      if (error == null && response.getStatus() != null && response.getStatus().isError()) {
        error = response.getStatus().getThrowable();
      }
      instrumenter.end(context, request, response, error);
    }

    return CONTINUE;
  }
}
