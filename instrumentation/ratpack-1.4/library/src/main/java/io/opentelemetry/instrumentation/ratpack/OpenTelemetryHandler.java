package io.opentelemetry.instrumentation.ratpack;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.concurrent.atomic.AtomicReference;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.http.Request;
import ratpack.http.Response;

final class OpenTelemetryHandler implements Handler {

  private final Instrumenter<Request, Response> instrumenter;

  OpenTelemetryHandler(
      Instrumenter<Request, Response> instrumenter) {this.instrumenter = instrumenter;}

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
    AtomicReference<Throwable> thrown = new AtomicReference<>();
    context.onClose(outcome -> {
      // Route not available in beginning of request so handle it manually here.
      String route = '/' + context.getPathBinding().getDescription();
      Span span = Span.fromContext(otelCtx);
      span.updateName(route);
      span.setAttribute(SemanticAttributes.HTTP_ROUTE, route);
      instrumenter.end(otelCtx, outcome.getRequest(), context.getResponse(), thrown.get());
    });

    try (Scope ignored = otelCtx.makeCurrent()) {
      context.next();
    } catch (Throwable t) {
      thrown.set(t.getCause());
      throw t;
    }
  }
}
