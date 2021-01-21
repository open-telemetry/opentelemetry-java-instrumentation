package io.opentelemetry.instrumentation.api.tracer.binding;

import io.opentelemetry.api.trace.SpanBuilder;

@FunctionalInterface
public interface AttributeBinding {
    SpanBuilder apply(SpanBuilder builder, Object arg);
}
