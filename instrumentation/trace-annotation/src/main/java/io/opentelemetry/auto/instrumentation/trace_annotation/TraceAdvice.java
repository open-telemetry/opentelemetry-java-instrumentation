package io.opentelemetry.auto.instrumentation.trace_annotation;

import static io.opentelemetry.auto.instrumentation.trace_annotation.TraceDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.trace_annotation.TraceDecorator.TRACER;

import io.opentelemetry.auto.api.MoreTags;
import io.opentelemetry.auto.instrumentation.api.SpanScopePair;
import io.opentelemetry.trace.Span;
import java.lang.reflect.Method;
import net.bytebuddy.asm.Advice;

public class TraceAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static SpanScopePair onEnter(@Advice.Origin final Method method) {
    final Span span = TRACER.spanBuilder("trace.annotation").startSpan();
    final String resourceName = DECORATE.spanNameForMethod(method);
    span.setAttribute(MoreTags.RESOURCE_NAME, resourceName);
    DECORATE.afterStart(span);
    return new SpanScopePair(span, TRACER.withSpan(span));
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Enter final SpanScopePair spanScopePair, @Advice.Thrown final Throwable throwable) {
    final Span span = spanScopePair.getSpan();
    DECORATE.onError(span, throwable);
    DECORATE.beforeFinish(span);
    span.end();
    spanScopePair.getScope().close();
  }
}
