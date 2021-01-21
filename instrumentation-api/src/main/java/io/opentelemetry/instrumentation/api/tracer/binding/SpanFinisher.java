package io.opentelemetry.instrumentation.api.tracer.binding;

import io.opentelemetry.api.trace.Span;

/**
 * Abstracts how a method span should be finished based on the result
 * of the traced method.  The success of the traced method is determined
 * by the presence (or lack thereof) of an {@link Throwable} instance
 * passed to the {@code error} parameter of the
 * {@link SpanFinisher#finish} method.
 */
@FunctionalInterface
public interface SpanFinisher {
    /**
     *
     * @param span the {@link Span} to finish
     * @param result the successful result of the method being traced
     * @param error the unsuccessful result of the method being traced
     * @return the successful result of the method
     */
    Object finish(Span span, Object result, Throwable error);
}
