package io.opentelemetry.instrumentation.api.tracer.binding;

import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import org.aspectj.lang.ProceedingJoinPoint;

@FunctionalInterface
public interface TraceBinding {
  SpanBuilder apply(Tracer tracer, ProceedingJoinPoint joinPoint);
}
