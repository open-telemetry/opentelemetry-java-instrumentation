package io.opentelemetry.auto.instrumentation.vertx;

import io.opentelemetry.trace.Span;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

import static io.opentelemetry.auto.instrumentation.vertx.VertxDecorator.TRACER;


/**
 * This is used to wrap Vert.x Handlers to provide nice user-friendly SERVER span names
 */
@Slf4j
public final class RoutingContextHandlerWrapper implements Handler<RoutingContext> {
  private final Handler<RoutingContext> handler;

  public RoutingContextHandlerWrapper(final Handler<RoutingContext> handler) {
    this.handler = handler;
  }

  @Override
  public void handle(RoutingContext context) {
    try {
      Span currentSpan = TRACER.getCurrentSpan();
      if (currentSpan.getContext().isValid()) {
        //TODO should update only SERVER span using
        //https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/465
        currentSpan.updateName(context.currentRoute().getPath());
      }
    } catch (Exception ex) {
      log.error("Failed to update server span name with vert.x route", ex);
    }
    handler.handle(context);
  }
}
