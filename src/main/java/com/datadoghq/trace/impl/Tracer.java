package com.datadoghq.trace.impl;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;


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
        private Map<String, Object> tags = new HashMap();
        private Long timestamp;
        private Optional<SpanContext> parent;

        public SpanBuilder(String operationName) {
            this.operationName = operationName;
        }

        public io.opentracing.Tracer.SpanBuilder asChildOf(SpanContext spanContext) {
            return null;
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
            SpanContext context = buildNewSpanContext();

            return new com.datadoghq.trace.impl.Span(
                    this.operationName,
                    this.tags,
                    Optional.ofNullable(this.timestamp),
                    context);
        }

        private SpanContext buildNewSpanContext() {

            Optional<Object> parentContext = Optional.ofNullable(this.parent);
            SpanContext context = new com.datadoghq.trace.impl.SpanContext(

            );



            if (this.parent == null) {
                long traceId = 123L;

            } else {

            }

            return null;
        }

        public Iterable<Map.Entry<String, String>> baggageItems() {
            return null;
        }
    }
}
