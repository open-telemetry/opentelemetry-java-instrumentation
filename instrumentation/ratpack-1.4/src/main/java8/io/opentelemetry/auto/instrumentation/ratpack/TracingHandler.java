package io.opentelemetry.auto.instrumentation.ratpack;

import static io.opentelemetry.auto.instrumentation.ratpack.RatpackServerDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.ratpack.RatpackServerDecorator.TRACER;

import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.http.Request;

public final class TracingHandler implements Handler {
  public static Handler INSTANCE = new TracingHandler();

  /**
   * This constant is copied over from io.opentelemetry.auto.instrumentation.netty41.AttributeKeys.
   * The key string must be kept consistent.
   */
  public static final AttributeKey<Span> SERVER_ATTRIBUTE_KEY =
      AttributeKey.valueOf(
          "io.opentelemetry.auto.instrumentation.netty41.server.HttpServerTracingHandler.span");

  @Override
  public void handle(final Context ctx) {
    final Request request = ctx.getRequest();

    final Attribute<Span> spanAttribute =
        ctx.getDirectChannelAccess().getChannel().attr(SERVER_ATTRIBUTE_KEY);
    final Span nettySpan = spanAttribute.get();

    // Relying on executor instrumentation to assume the netty span is in context as the parent.
    final Span ratpackSpan = TRACER.spanBuilder("ratpack.handler").startSpan();
    DECORATE.afterStart(ratpackSpan);
    DECORATE.onConnection(ratpackSpan, request);
    DECORATE.onRequest(ratpackSpan, request);
    ctx.getExecution().add(ratpackSpan);

    ctx.getResponse()
        .beforeSend(
            response -> {
              try (final Scope ignored = TRACER.withSpan(ratpackSpan)) {
                if (nettySpan != null) {
                  // Rename the netty span resource name with the ratpack route.
                  DECORATE.onContext(nettySpan, ctx);
                }
                DECORATE.onResponse(ratpackSpan, response);
                DECORATE.onContext(ratpackSpan, ctx);
                DECORATE.beforeFinish(ratpackSpan);
                ratpackSpan.end();
              }
            });

    try (final Scope scope = TRACER.withSpan(ratpackSpan)) {
      ctx.next();
    } catch (final Throwable e) {
      DECORATE.onError(ratpackSpan, e);
      // will be finished in above response handler
      throw e;
    }
  }
}
