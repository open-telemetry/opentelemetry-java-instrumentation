package com.datadoghq.trace.impl;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;


public class Tracer implements io.opentracing.Tracer {

    public SpanBuilder buildSpan(String operationName) {
        return new SpanBuilder(operationName);
    }

    public <C> void inject(SpanContext spanContext, Format<C> format, C c) {

    }

    public <C> SpanContext extract(Format<C> format, C c) {
        return null;
    }

    class SpanBuilder implements io.opentracing.Tracer.SpanBuilder {

        private final String operationName;
        private Map<String, Object> tags = new HashMap<String, Object>();
        private Long timestamp;
        private Optional<com.datadoghq.trace.impl.SpanContext> parent = Optional.empty();

        public SpanBuilder(String operationName) {
            this.operationName = operationName;
        }

        public io.opentracing.Tracer.SpanBuilder asChildOf(SpanContext spanContext) {
            this.parent = Optional.ofNullable((com.datadoghq.trace.impl.SpanContext)spanContext);
            return this;
        }

        public io.opentracing.Tracer.SpanBuilder asChildOf(Span span) {
            return null;
        }

        public io.opentracing.Tracer.SpanBuilder addReference(String s, SpanContext spanContext) {
            return null;
        }


        public io.opentracing.Tracer.SpanBuilder withTag(String tag, Number number) {
            return withTag(tag, (Object) number);
        }

        public io.opentracing.Tracer.SpanBuilder withTag(String tag, String string) {
            return withTag(tag, (Object) string);
        }

        public io.opentracing.Tracer.SpanBuilder withTag(String tag, boolean bool) {
            return withTag(tag, (Object) bool);
        }

        private io.opentracing.Tracer.SpanBuilder withTag(String tag, Object value) {
            this.tags.put(tag, value);
            return this;
        }


        public io.opentracing.Tracer.SpanBuilder withStartTimestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Span start() {

            // build the context
            com.datadoghq.trace.impl.SpanContext context = buildTheSpanContext();

            return new com.datadoghq.trace.impl.Span(
                    Tracer.this,
                    this.operationName,
                    this.tags,
                    Optional.ofNullable(this.timestamp),
                    context);
        }

        private com.datadoghq.trace.impl.SpanContext buildTheSpanContext() {

        	com.datadoghq.trace.impl.SpanContext context = null;

            long generatedId = generateNewId();
            if (parent.isPresent()) {
                com.datadoghq.trace.impl.SpanContext p = parent.get();
                context = new com.datadoghq.trace.impl.SpanContext(
                        p.getTraceId(),
                        generatedId,
                        p.getSpanId(),
                        null,
                        null,
                        null,
                        false,
                        null,
                        null,
                        true);
            } else {
                context = new com.datadoghq.trace.impl.SpanContext(
                        generatedId,
                        generatedId,
                        0L,
                        null,
                        null,
                        null,
                        false,
                        null,
                        null,
                        true);
            }

            return context;
        }

        public Iterable<Map.Entry<String, String>> baggageItems() {
            return null;
        }
    }

    long generateNewId() {
        return UUID.randomUUID().getMostSignificantBits();
    }
}
