package io.opentelemetry.instrumentation.api.tracer.strategy.reactor;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.instrumentation.api.tracer.strategy.SpanStrategy;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Base class for strategies that trace methods that return reactive
 * publishers, e.g. {@link Mono} or {@link Flux}.  These publishers can
 * support multiple subscriptions which are each traced as a separate span.
 */
public abstract class ReactiveSpanStrategy implements SpanStrategy {

    protected  <T, P extends Publisher<T>> Function<P, P> withCheckpoint(Span span, BiFunction<P, String, P> checkpoint) {
        return publisher -> checkpoint.apply(publisher, span.toString());
    }

    /**
     * Creates a {@link Mono} of the {@link Span} for the current subscription.
     * @param spanBuilder used to create new {@link Span} instances
     * @param existingSpan the initial {@link Span} used to trace the method invocation
     *                     and the first subscription
     * @return a {@link Mono} wrapping the {@link Span}
     */
    protected Mono<Span> createSpanMono(SpanBuilder spanBuilder, Span existingSpan) {
        AtomicBoolean flag = new AtomicBoolean(false);

        return Mono.defer(() -> {
            if (flag.compareAndSet(false, true)) {
                return Mono.just(existingSpan);
            } else {
                return Mono.just(spanBuilder.startSpan());
            }
        });
    }
}
