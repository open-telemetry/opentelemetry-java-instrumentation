/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v1_0;

import static io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming.Source.CONTROLLER;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming;
import io.opentelemetry.instrumentation.restlet.v1_0.internal.RestletServerSpanNaming;
import org.restlet.Filter;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;

final class TracingFilter extends Filter {

  private static final ThreadLocal<ContextAndScope> otelContextAndScope = new ThreadLocal<>();
  private final Instrumenter<Request, Response> instrumenter;
  private final String path;

  public TracingFilter(Instrumenter<Request, Response> instrumenter, String path) {
    this.instrumenter = instrumenter;
    this.path = path;
  }

  @Override
  public int doHandle(Request request, Response response) {
    try {
      super.doHandle(request, response);
    } catch (Throwable t) {
      response.setStatus(new Status(Status.SERVER_ERROR_INTERNAL, t));
    }
    return CONTINUE;
  }

  @Override
  protected int beforeHandle(Request request, Response response) {

    Context parentContext = Context.current();
    Context context = parentContext;

    if (instrumenter.shouldStart(parentContext, request)) {
      context = instrumenter.start(parentContext, request);
      Scope scope = context.makeCurrent();
      otelContextAndScope.set(ContextAndScope.create(context, scope));
    }

    ServerSpanNaming.updateServerSpanName(
        context, CONTROLLER, RestletServerSpanNaming.SERVER_SPAN_NAME, path);

    return CONTINUE;
  }

  @Override
  protected void afterHandle(Request request, Response response) {

    ContextAndScope contextAndScope = otelContextAndScope.get();

    if (contextAndScope == null) {
      return;
    }

    otelContextAndScope.remove();

    contextAndScope.getScope().close();

    // Restlet suppresses exceptions and sets the throwable in status
    Throwable statusThrowable = null;
    if (response.getStatus() != null && response.getStatus().isError()) {
      statusThrowable = response.getStatus().getThrowable();
    }

    instrumenter.end(contextAndScope.getContext(), request, response, statusThrowable);
  }
}
