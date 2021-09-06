/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v1_0;

import static io.opentelemetry.instrumentation.restlet.v1_0.RestletSingletons.instrumenter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.ServerSpan;
import org.restlet.Filter;
import org.restlet.data.Request;
import org.restlet.data.Response;

public final class TracingFilter extends Filter {

  private static final ThreadLocal<Scope> otelScope = new ThreadLocal<>();
  private static final ThreadLocal<Context> otelContext = new ThreadLocal<>();
  private final String path;

  public TracingFilter(String path) {
    this.path = path;
  }

  @Override
  protected int beforeHandle(Request request, Response response) {

    Context parentContext = Context.current();

    if (!instrumenter().shouldStart(parentContext, request)) {
      return CONTINUE;
    }

    otelContext.set(instrumenter().start(parentContext, request));

    Span span = ServerSpan.fromContextOrNull(otelContext.get());

    if (span != null) {
      span.updateName(path);
      otelScope.set(otelContext.get().makeCurrent());
    }

    return CONTINUE;
  }

  @Override
  protected void afterHandle(Request request, Response response) {

    if (otelScope.get() == null) {
      return;
    }

    otelScope.get().close();

    if (otelContext.get() == null) {
      return;
    }

    // Restlet suppresses exceptions and sets the throwable in status
    Throwable statusThrowable = response.getStatus().getThrowable();

    instrumenter().end(otelContext.get(), request, response, statusThrowable);
  }
}
