package io.opentelemetry.auto.instrumentation.trace_annotation;

import static io.opentelemetry.auto.instrumentation.trace_annotation.TraceDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.trace_annotation.TraceDecorator.TRACER;

import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.trace.Span;
import java.lang.reflect.Method;
import net.bytebuddy.asm.Advice;

public class TraceAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static SpanWithScope onEnter(@Advice.Origin final Method method) {
    final Span span = TRACER.spanBuilder("trace.annotation").startSpan();
    final String resourceName = DECORATE.spanNameForMethod(method);
    span.setAttribute(MoreTags.RESOURCE_NAME, resourceName);
    DECORATE.afterStart(span);
    return new SpanWithScope(span, TRACER.withSpan(span));
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Enter final SpanWithScope spanWithScope, @Advice.Thrown final Throwable throwable) {
    final Span span = spanWithScope.getSpan();
    DECORATE.onError(span, throwable);
    DECORATE.beforeFinish(span);
    span.end();
    spanWithScope.getScope().close();
  }
}
