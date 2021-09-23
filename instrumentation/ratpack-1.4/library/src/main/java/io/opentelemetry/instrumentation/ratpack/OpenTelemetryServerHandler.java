/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import ratpack.error.ServerErrorHandler;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.http.Request;
import ratpack.http.Response;

final class OpenTelemetryServerHandler implements Handler {

  private final Instrumenter<Request, Response> instrumenter;

  OpenTelemetryServerHandler(Instrumenter<Request, Response> instrumenter) {
    this.instrumenter = instrumenter;
  }

  @Override
  public void handle(Context context) {
    Request request = context.getRequest();

    io.opentelemetry.context.Context parentOtelCtx = io.opentelemetry.context.Context.current();
    if (!instrumenter.shouldStart(parentOtelCtx, request)) {
      context.next();
      return;
    }

    io.opentelemetry.context.Context otelCtx = instrumenter.start(parentOtelCtx, request);
    context.getExecution().add(io.opentelemetry.context.Context.class, otelCtx);
    context.onClose(
        outcome -> {
          // Route not available in beginning of request so handle it manually here.
          String route = '/' + context.getPathBinding().getDescription();
          Span span = Span.fromContext(otelCtx);
          span.updateName(route);
          span.setAttribute(SemanticAttributes.HTTP_ROUTE, route);

          Throwable error =
              context.getExecution().maybeGet(ErrorHolder.class).map(ErrorHolder::get).orElse(null);

          instrumenter.end(otelCtx, outcome.getRequest(), context.getResponse(), error);
        });

    // An execution continues to execute synchronously until it is unbound from a thread. We need
    // to make the context current here to make it available to the next handler (possibly user
    // code) but close the scope at the end of the ExecInterceptor, which corresponds to when the
    // execution is about to be unbound from the thread.
    Scope scope = otelCtx.makeCurrent();
    context.getExecution().add(Scope.class, scope);

    // A user may have defined their own ServerErrorHandler, so we add ours to the Execution which
    // has higher precedence.
    context.getExecution().add(ServerErrorHandler.class, OpenTelemetryServerErrorHandler.INSTANCE);
    context.next();
  }

  static final class ErrorHolder {
    private final Throwable throwable;

    ErrorHolder(Throwable throwable) {
      this.throwable = throwable;
    }

    Throwable get() {
      return throwable;
    }
  }
}
