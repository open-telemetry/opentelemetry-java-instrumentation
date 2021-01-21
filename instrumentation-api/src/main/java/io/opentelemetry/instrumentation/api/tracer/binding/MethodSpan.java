package io.opentelemetry.instrumentation.api.tracer.binding;

import io.opentelemetry.api.trace.Span;

/**
 * Represents the invocation of a traced method.  The {@link MethodSpan#complete}
 * method is called when the method completes either successfully or by throwing
 * an exception.
 */
@FunctionalInterface
public interface MethodSpan {
    /**
     * Called to indicate that the traced method has completed, either successfully
     * or by throwing an exception.
     * @param result the successful result of the traced method
     * @param error the exception thrown by the traced method
     * @param finish the {@link SpanFinisher} to be used to finish tracing {@link Span}s.
     * @return the result of the method span which may decorate the successful
     *         result of the traced method to allow the {@link SpanStrategy} to
     *         optionally register for completion of an asynchronous result
     */
    Object complete(Object result, Throwable error, SpanFinisher finish);
}
