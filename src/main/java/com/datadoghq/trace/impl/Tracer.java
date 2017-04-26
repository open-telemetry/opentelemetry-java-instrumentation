package com.datadoghq.trace.impl;

import java.util.*;

import com.datadoghq.trace.Utils.TracerLogger;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;


public class Tracer implements io.opentracing.Tracer {

    private TracerLogger logger = new TracerLogger();

    public SpanBuilder buildSpan(String operationName) {
        return new SpanBuilder(operationName);
    }

    public <C> void inject(SpanContext spanContext, Format<C> format, C c) {
        throw new UnsupportedOperationException();

    }

    public <C> SpanContext extract(Format<C> format, C c) {
        throw new UnsupportedOperationException();
    }

    public class SpanBuilder implements io.opentracing.Tracer.SpanBuilder {

        private final String operationName;
        private Map<String, Object> tags = new HashMap<>();
        private Long timestamp;
        private DDSpan parent;
        private String serviceName;
        private String resourceName;
        private boolean errorFlag;
        private String spanType;

        public SpanBuilder(String operationName) {
            this.operationName = operationName;
        }

        public Tracer.SpanBuilder asChildOf(SpanContext spanContext) {
            throw new UnsupportedOperationException("Should be a complete span");
            //this.parent = spanContext;
            //return this;
        }

        public Tracer.SpanBuilder asChildOf(Span span) {
            this.parent = (DDSpan) span;
            return this;
        }

        public Tracer.SpanBuilder addReference(String referenceType, SpanContext spanContext) {
            throw new UnsupportedOperationException();
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
            logger.startNewSpan(this.operationName, context.getSpanId());

            LinkedHashSet traces = null;
            if (this.parent != null) {
                traces = parent.getTraces();
            }

            return new DDSpan(
                    Tracer.this,
                    this.operationName,
                    traces,
                    this.tags,
                    this.timestamp,
                    context);
        }

        private DDSpanContext buildTheSpanContext() {

            DDSpanContext context;

            long generatedId = generateNewId();
            if (this.parent != null) {
                DDSpanContext p = (DDSpanContext) this.parent.context();
                context = new DDSpanContext(
                        p.getTraceId(),
                        generatedId,
                        p.getSpanId(),
                        Optional.ofNullable(p.getServiceName()).orElse(this.serviceName),
                        Optional.ofNullable(this.resourceName).orElse(this.operationName),
                        p.getBaggageItems(),
                        errorFlag,
                        null,
                        this.spanType,
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
                        this.spanType,
                        true);
            }

            return context;
        }

        public Iterable<Map.Entry<String, String>> baggageItems() {
            if (parent == null) {
                return Collections.emptyList();
            }
            return parent.context().baggageItems();
        }
    }

    long generateNewId() {
        return System.nanoTime();
    }
}
