package io.opentelemetry.instrumentation.api.tracer.strategy;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.binding.MethodSpan;

/**
 * Strategy to trace methods that execute synchronously and where the tracing span
 * is to be finished immediately on completion of the method invocation.
 */
public class SynchronousSpanStrategy implements SpanStrategy {

    @Override
    public MethodSpan startMethodSpan(Context currentContext, SpanBuilder spanBuilder) {
        Span span = spanBuilder.startSpan();
        Scope scope = currentContext.with(span).makeCurrent();
        return (result, exception, finisher) -> {
            scope.close();
            return finisher.finish(span, result, exception);
        };
    }
}
