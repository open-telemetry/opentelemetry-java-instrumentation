package io.opentelemetry.instrumentation.api.tracer.strategy;

import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.binding.MethodSpan;

/**
 * Represents a strategy for tracing spans around method calls.
 */
@FunctionalInterface
public interface SpanStrategy {
    /**
     * Starts a span around the method being traced.
     * @param currentContext the context for the current thread
     * @param spanBuilder a span builder used to create spans
     * @return a {@link MethodSpan} used as a callback for when the method completes
     */
    MethodSpan startMethodSpan(Context currentContext, SpanBuilder spanBuilder);
}
