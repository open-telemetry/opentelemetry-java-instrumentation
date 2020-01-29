package io.opentelemetry.auto.test.base;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.api.MoreTags;
import io.opentelemetry.auto.instrumentation.api.SpanScopePair;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import net.bytebuddy.asm.Advice;

public abstract class HttpServerTestAdvice {

  public static final Tracer TRACER = OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto");

  /**
   * This advice should be applied at the root of a http server request to validate the
   * instrumentation correctly ignores other traces.
   */
  public static class ServerEntryAdvice {
    @Advice.OnMethodEnter
    public static SpanScopePair methodEnter() {
      if (!HttpServerTest.ENABLE_TEST_ADVICE.get()) {
        // Skip if not running the HttpServerTest.
        return null;
      }
      if (TRACER.getCurrentSpan().getContext().isValid()) {
        return null;
      } else {
        final Span span = TRACER.spanBuilder("TEST_SPAN").startSpan();
        span.setAttribute(MoreTags.RESOURCE_NAME, "ServerEntry");
        return new SpanScopePair(span, TRACER.withSpan(span));
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void methodExit(@Advice.Enter final SpanScopePair spanScopePair) {
      if (spanScopePair != null) {
        spanScopePair.getSpan().end();
        spanScopePair.getScope().close();
      }
    }
  }
}
