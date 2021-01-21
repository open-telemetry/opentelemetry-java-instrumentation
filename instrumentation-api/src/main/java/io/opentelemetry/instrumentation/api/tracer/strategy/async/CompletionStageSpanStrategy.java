package io.opentelemetry.instrumentation.api.tracer.strategy.async;

import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.binding.MethodSpan;
import io.opentelemetry.instrumentation.api.tracer.binding.SpanFinisher;
import io.opentelemetry.instrumentation.api.tracer.strategy.SpanStrategy;

/**
 * Strategy to trace methods that return {@link CompletionStage} instances that
 * represent an asynchronous operation in-flight.  The {@link Span} traced for the
 * method will not be finished until the future is completed.
 */
public class CompletionStageSpanStrategy implements SpanStrategy {

    @Override
    public MethodSpan startMethodSpan(Context currentContext, SpanBuilder spanBuilder) {
        Span span = spanBuilder.startSpan();
        Scope scope = currentContext.with(span).makeCurrent();
        return (result, error, finisher) -> {
            try (Scope ignored = scope) {
                if (result instanceof CompletionStage) {
                    CompletionStage<?> stage = (CompletionStage<?>) result;
                    return stage.whenComplete(finishSpan(span, finisher));
                } else {
                    return finisher.finish(span, result, error);
                }
            }
        };
    }

    private BiConsumer<Object, Throwable> finishSpan(Span span, SpanFinisher finisher) {
        return (result, error) -> finisher.finish(span, result, error);
    }
}
