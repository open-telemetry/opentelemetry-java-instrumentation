package io.opentelemetry.instrumentation.api.tracer.strategy;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import io.opentelemetry.instrumentation.api.tracer.strategy.async.CompletableFutureSpanStrategy;
import io.opentelemetry.instrumentation.api.tracer.strategy.async.CompletionStageSpanStrategy;


/**
 * Registers strategies for tracing spans around traced methods based on the
 * return type of the method being traced.
 */
public class SpanStrategyFactory {

    private final Map<Class<?>, SpanStrategy> strategies;
    private final SpanStrategy defaultStrategy;

    /**
     * Creates a new instance of the factory with the default strategies.
     */
    public SpanStrategyFactory() {
        strategies = new HashMap<>();
        defaultStrategy = new SynchronousSpanStrategy();
        register(CompletionStage.class, new CompletionStageSpanStrategy());
        register(CompletableFuture.class, new CompletableFutureSpanStrategy());
    }

    /**
     * Registers a strategy for tracing spans around a traced method.
     * @param returnClass return type of the method being traced
     * @param strategy strategy to use when tracing the method
     * @return this factory
     */
    public SpanStrategyFactory register(Class<?> returnClass, SpanStrategy strategy) {
        strategies.put(returnClass, strategy);
        return this;
    }

    /**
     * Returns the span strategy used for tracing a traced method based
     * on the return type of the method.
     * @param returnClass return type of the method being traced
     * @return the strategy to use when tracing the method
     */
    public SpanStrategy createSpanStrategy(Class<?> returnClass) {
        return strategies.getOrDefault(returnClass, defaultStrategy);
    }
}
