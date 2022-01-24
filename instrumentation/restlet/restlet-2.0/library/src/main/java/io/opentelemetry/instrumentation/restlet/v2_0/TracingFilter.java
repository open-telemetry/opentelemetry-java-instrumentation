/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v2_0;

import static io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteSource.CONTROLLER;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteHolder;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.resource.ResourceException;
import org.restlet.routing.Filter;

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

    HttpRouteHolder.updateHttpRoute(context, CONTROLLER, (ctx, s) -> s, path);

    Throwable statusThrowable = null;
    try {
      super.doHandle(request, response);
    } catch (Throwable t) {

      statusThrowable = t;

      if (t instanceof Error || t instanceof RuntimeException) {
        throw t;
      } else {
        throw new ResourceException(t);
      }

    } finally {

      if (scope != null) {

        scope.close();
        if (response.getStatus() != null && response.getStatus().isError()) {
          statusThrowable = response.getStatus().getThrowable();
        }
        instrumenter.end(context, request, response, statusThrowable);
      }
    }

    return CONTINUE;
  }
}
