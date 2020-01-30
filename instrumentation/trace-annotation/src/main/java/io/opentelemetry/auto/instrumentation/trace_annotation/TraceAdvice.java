package io.opentelemetry.auto.instrumentation.trace_annotation;

import static io.opentelemetry.auto.instrumentation.trace_annotation.TraceDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.trace_annotation.TraceDecorator.TRACER;

import io.opentelemetry.auto.api.MoreTags;
import io.opentelemetry.auto.api.Trace;
import io.opentelemetry.auto.instrumentation.api.SpanScopePair;
import io.opentelemetry.trace.Span;
import java.lang.reflect.Method;
import net.bytebuddy.asm.Advice;

public class TraceAdvice {
  private static final String DEFAULT_OPERATION_NAME = "trace.annotation";

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static SpanScopePair onEnter(@Advice.Origin final Method method) {
    final Trace traceAnnotation = method.getAnnotation(Trace.class);
    String operationName = traceAnnotation == null ? null : traceAnnotation.operationName();
    if (operationName == null || operationName.isEmpty()) {
      operationName = DEFAULT_OPERATION_NAME;
    }

    final Span span = TRACER.spanBuilder(operationName).startSpan();

    String resourceName = traceAnnotation == null ? null : traceAnnotation.resourceName();
    if (resourceName == null || resourceName.isEmpty()) {
      resourceName = DECORATE.spanNameForMethod(method);
    }
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
