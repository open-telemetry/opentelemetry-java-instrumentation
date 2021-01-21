package io.opentelemetry.instrumentation.api.tracer.strategy.reactor;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.function.Function;
import io.opentelemetry.instrumentation.api.tracer.binding.MethodSpan;
import io.opentelemetry.instrumentation.api.tracer.binding.SpanFinisher;
import io.opentelemetry.instrumentation.api.tracer.strategy.SpanStrategy;
import reactor.core.publisher.Mono;

/**
 * Strategy to trace methods that return a reactive {@link Mono} instance representing
 * an asynchronous operation.  The {@link Span} traced for the method isn't finished
 * until an observer subscribes to the {@link Mono} and the publisher completes.  If
 * subsequent observers subscribe additional {@link Span} instances are traced.
 */
public class MonoSpanStrategy extends ReactiveSpanStrategy implements SpanStrategy {

    @Override
    public MethodSpan startMethodSpan(Context currentContext, SpanBuilder spanBuilder) {

        Span span = spanBuilder.startSpan();
        Scope scope = currentContext.with(span).makeCurrent();
        return (result, error, finisher) -> {
            try (Scope ignored = scope) {
                if (result instanceof Mono) {
                    Mono<?> mono = (Mono<?>) result;
                    return createSpanMono(spanBuilder, span)
                            .flatMap(recordMonoCompletion(mono, finisher));
                } else {
                    return finisher.finish(span, result, error);
                }
            }
        };
    }

    private Function<Span, Mono<?>> recordMonoCompletion(Mono<?> mono, SpanFinisher finisher) {
        return span -> mono.transform(withCheckpoint(span, Mono::checkpoint))
                .doOnError(error -> finisher.finish(span, null, error))
                .doOnSuccess(result -> finisher.finish(span, result, null))
                .doOnCancel(() -> finisher.finish(span, null, null));
    }
}
