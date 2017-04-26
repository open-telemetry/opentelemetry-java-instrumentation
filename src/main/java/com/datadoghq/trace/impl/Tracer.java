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

    public class SpanBuilder implements io.opentracing.Tracer.SpanBuilder {

        private final String operationName;
        private Map<String, Object> tags = new HashMap<String,Object>();
        private Long timestamp;
        private SpanContext parent;
        private String serviceName;
        private String resourceName;
        private boolean errorFlag;
        private String spanType;

        public SpanBuilder(String operationName) {
            this.operationName = operationName;
        }

        public Tracer.SpanBuilder asChildOf(SpanContext spanContext) {
            this.parent = spanContext;
            return this;
        }

        public Tracer.SpanBuilder asChildOf(Span span) {
            return asChildOf(span.context());
        }

        public Tracer.SpanBuilder addReference(String referenceType, SpanContext spanContext) {

            if (References.CHILD_OF.equals(referenceType) || References.FOLLOWS_FROM.equals(referenceType)) {
                // @todo: implements the notion of referenceType, currently only link a span to a parent one
                return asChildOf(spanContext);
            } else {
                // do nothing
                return this;
            }
        }

        public Tracer.SpanBuilder withTag(String tag, Number number) {
            return withTag(tag, (Object) number);
        }

        public Tracer.SpanBuilder withTag(String tag, String string) {
            return withTag(tag, (Object) string);
        }

        public Tracer.SpanBuilder withTag(String tag, boolean bool) {
            return withTag(tag, (Object) bool);
        }

        private Tracer.SpanBuilder withTag(String tag, Object value) {
            this.tags.put(tag, value);
            return this;
        }

        public Tracer.SpanBuilder withStartTimestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Tracer.SpanBuilder withServiceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }


        public Tracer.SpanBuilder withResourceName(String resourceName) {
            this.resourceName = resourceName;
            return this;
        }

        public Tracer.SpanBuilder withErrorFlag() {
            this.errorFlag = true;
            return this;
        }

        public Tracer.SpanBuilder withSpanType(String spanType) {
            this.spanType = spanType;
            return this;
        }



        public Span start() {

            // build the context
            DDSpanContext context = buildTheSpanContext();

            return new DDSpan(
                    Tracer.this,
                    this.operationName,
                    this.tags,
                    this.timestamp,
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
                        Optional.ofNullable(p.getServiceName()).orElse(this.serviceName),
                        Optional.ofNullable(this.resourceName).orElse(this.operationName),
                        p.getBaggageItems(),
                        errorFlag,
                        null,
                        null,
                        true
                );
            } else {
                context = new DDSpanContext(
                        generatedId,
                        generatedId,
                        0L,
                        this.serviceName,
                        Optional.ofNullable(this.resourceName).orElse(this.operationName),
                        null,
                        errorFlag,
                        null,
                        null,
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
        return System.nanoTime();
    }
}
