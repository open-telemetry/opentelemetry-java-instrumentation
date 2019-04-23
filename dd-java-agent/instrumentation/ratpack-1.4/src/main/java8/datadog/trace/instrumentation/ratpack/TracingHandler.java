package datadog.trace.instrumentation.ratpack;

import static datadog.trace.instrumentation.ratpack.RatpackServerDecorator.DECORATE;

import datadog.trace.context.TraceScope;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.http.Request;

public final class TracingHandler implements Handler {
  public static Handler INSTANCE = new TracingHandler();

  /**
   * This constant is copied over from datadog.trace.instrumentation.netty41.AttributeKeys. The key
   * string must be kept consistent.
   */
  public static final AttributeKey<Span> SERVER_ATTRIBUTE_KEY =
      AttributeKey.valueOf(
          "datadog.trace.instrumentation.netty41.server.HttpServerTracingHandler.span");

  @Override
  public void handle(final Context ctx) {
    final Tracer tracer = GlobalTracer.get();
    final Request request = ctx.getRequest();

    final Attribute<Span> spanAttribute =
        ctx.getDirectChannelAccess().getChannel().attr(SERVER_ATTRIBUTE_KEY);
    final Span nettySpan = spanAttribute.get();

    // Relying on executor instrumentation to assume the netty span is in context as the parent.
    final Span ratpackSpan = tracer.buildSpan("ratpack.handler").start();
    DECORATE.afterStart(ratpackSpan);
    DECORATE.onConnection(ratpackSpan, request);
    DECORATE.onRequest(ratpackSpan, request);
    ctx.getExecution().add(ratpackSpan);

    try (final Scope scope = tracer.scopeManager().activate(ratpackSpan, false)) {
      if (scope instanceof TraceScope) {
        ((TraceScope) scope).setAsyncPropagation(true);
      }

      ctx.getResponse()
          .beforeSend(
              response -> {
                try (final Scope ignored = tracer.scopeManager().activate(ratpackSpan, false)) {
                  if (nettySpan != null) {
                    // Rename the netty span resource name with the ratpack route.
                    DECORATE.onContext(nettySpan, ctx);
                  }
                  DECORATE.onResponse(ratpackSpan, response);
                  DECORATE.onContext(ratpackSpan, ctx);
                  DECORATE.beforeFinish(ratpackSpan);
                  ratpackSpan.finish();
                }
              });

      ctx.next();
    } catch (final Throwable e) {
      DECORATE.onError(ratpackSpan, e);
      DECORATE.beforeFinish(ratpackSpan);
      // finish since the callback probably didn't get added.
      ratpackSpan.finish();
      throw e;
    }
  }
}
