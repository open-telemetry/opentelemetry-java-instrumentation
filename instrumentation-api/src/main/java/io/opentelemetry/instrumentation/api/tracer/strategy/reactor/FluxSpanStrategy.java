package io.opentelemetry.instrumentation.api.tracer.strategy.reactor;

import java.util.function.Function;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.binding.MethodSpan;
import io.opentelemetry.instrumentation.api.tracer.binding.SpanFinisher;
import io.opentelemetry.instrumentation.api.tracer.strategy.SpanStrategy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


/**
 * Strategy to trace methods that return a reactive {@link Flux} instance representing
 * an asynchronous operation.  The {@link Flux} traced for the method isn't finished
 * until an observer subscribes to the {@link Mono} and the publisher completes.  If
 * subsequent observers subscribe additional {@link Span} instances are traced.
 */
public class FluxSpanStrategy extends ReactiveSpanStrategy implements SpanStrategy {

    @Override
    public MethodSpan startMethodSpan(Context currentContext, SpanBuilder spanBuilder) {

        Span span = spanBuilder.startSpan();
        Scope scope = currentContext.with(span).makeCurrent();
        return (result, error, finisher) -> {
            try (Scope ignored = scope) {
                if (result instanceof Flux) {
                    Flux<?> flux = (Flux<?>) result;
                    return createSpanMono(spanBuilder, span)
                            .flatMapMany(recordFluxCompletion(flux, finisher));
                } else {
                    return finisher.finish(span, result, error);
                }
            }
        };
    }

    private Function<Span, Flux<?>> recordFluxCompletion(Flux<?> flux, SpanFinisher finisher) {
        return span -> flux.transform(withCheckpoint(span, Flux::checkpoint))
                .doOnError(exception -> finisher.finish(span, null, exception))
                .doOnCancel(() -> finisher.finish(span, null, null))
                .doOnComplete(() -> finisher.finish(span, null, null));
    }
}
