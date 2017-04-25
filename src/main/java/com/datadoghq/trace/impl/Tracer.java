package com.datadoghq.trace.impl;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;

import java.util.Map;


public class Tracer implements io.opentracing.Tracer {
    public SpanBuilder buildSpan(String s) {
        return null;
    }

    public <C> void inject(SpanContext spanContext, Format<C> format, C c) {

    }

    public <C> SpanContext extract(Format<C> format, C c) {
        return null;
    }

    class SpanBuilder implements io.opentracing.Tracer.SpanBuilder{

        public io.opentracing.Tracer.SpanBuilder asChildOf(SpanContext spanContext) {
            return null;
        }

        public io.opentracing.Tracer.SpanBuilder asChildOf(Span span) {
            return null;
        }

        public io.opentracing.Tracer.SpanBuilder addReference(String s, SpanContext spanContext) {
            return null;
        }

        public io.opentracing.Tracer.SpanBuilder withTag(String s, String s1) {
            return null;
        }

        public io.opentracing.Tracer.SpanBuilder withTag(String s, boolean b) {
            return null;
        }

        public io.opentracing.Tracer.SpanBuilder withTag(String s, Number number) {
            return null;
        }

        public io.opentracing.Tracer.SpanBuilder withStartTimestamp(long l) {
            return null;
        }

        public Span start() {
            return null;
        }

        public Iterable<Map.Entry<String, String>> baggageItems() {
            return null;
        }
    }
}
