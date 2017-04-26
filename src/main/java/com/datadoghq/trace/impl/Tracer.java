package com.datadoghq.trace.impl;

import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;

import java.util.*;


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
        private Map<String, Object> tags = new HashMap<>();
        private Long timestamp;
        private SpanContext parent;

        public SpanBuilder(String operationName) {
            this.operationName = operationName;
        }

        public io.opentracing.Tracer.SpanBuilder asChildOf(SpanContext spanContext) {
            this.parent = spanContext;
            return this;
        }

        public io.opentracing.Tracer.SpanBuilder asChildOf(Span span) {
            return asChildOf(span.context());
        }

        public io.opentracing.Tracer.SpanBuilder addReference(String referenceType, SpanContext spanContext) {

            if (References.CHILD_OF.equals(referenceType) || References.FOLLOWS_FROM.equals(referenceType)) {
                // @todo: implements the notion of referenceType, currently only link a span to a parent one
                return asChildOf(spanContext);
            } else {
                // do nothing
                return this;
            }
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
            DDSpanContext context = buildTheSpanContext();


            return new DDSpan(
                    Tracer.this,
                    this.operationName,
                    this.tags,
                    Optional.ofNullable(this.timestamp),
                    context);
        }

        private DDSpanContext buildTheSpanContext() {

            DDSpanContext context;

            long generatedId = generateNewId();
            if (this.parent != null) {
                DDSpanContext p = (DDSpanContext) this.parent;
                context = new DDSpanContext(
                        p.getTraceId(),
                        generatedId,
                        p.getSpanId(),
                        p.getServiceName(),
                        (String) this.tags.getOrDefault(DDTags.RESOURCE.getKey(), ""),
                        p.getBaggageItems(),
                        this.tags.containsKey(Tags.ERROR.getKey()),
                        null,
                        (String) this.tags.getOrDefault(Tags.SPAN_KIND.getKey(), ""),
                        true
                );
            } else {
                context = new DDSpanContext(
                        generatedId,
                        generatedId,
                        0L,
                        (String) this.tags.getOrDefault(DDTags.SERVICE.getKey(), ""),
                        (String) this.tags.getOrDefault(DDTags.RESOURCE.getKey(), ""),
                        null,
                        this.tags.containsKey(Tags.ERROR.getKey()),
                        null,
                        (String) this.tags.getOrDefault(Tags.SPAN_KIND.getKey(), ""),
                        true);
            }

            return context;
        }

        public Iterable<Map.Entry<String, String>> baggageItems() {
            if (parent == null) {
                return Collections.emptyList();
            }
            return parent.baggageItems();
        }
    }

    long generateNewId() {
        return UUID.randomUUID().getMostSignificantBits();
    }
}
