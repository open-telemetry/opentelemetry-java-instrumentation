package com.datadoghq.trace.impl;

import java.util.*;

import com.datadoghq.trace.Utils.TracerLogger;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;


public class DDTracer implements io.opentracing.Tracer {

    private TracerLogger logger = new TracerLogger();

    public DDSpanBuilder buildSpan(String operationName) {
        return new DDSpanBuilder(operationName);
    }

    public <C> void inject(SpanContext spanContext, Format<C> format, C c) {
        throw new UnsupportedOperationException();

    }

    public <C> SpanContext extract(Format<C> format, C c) {
        throw new UnsupportedOperationException();
    }

    public class DDSpanBuilder implements SpanBuilder {

        private final String operationName;
        private Map<String, Object> tags = new HashMap<String, Object>();
        private Long timestamp;
        private DDSpan parent;
        private String serviceName;
        private String resourceName;
        private boolean errorFlag;
        private String spanType;

        public DDSpanBuilder(String operationName) {
            this.operationName = operationName;
        }

        public DDTracer.DDSpanBuilder asChildOf(SpanContext spanContext) {
            throw new UnsupportedOperationException("Should be a complete span");
            //this.parent = spanContext;
            //return this;
        }

        public DDTracer.DDSpanBuilder asChildOf(Span span) {
            this.parent = (DDSpan) span;
            return this;
        }

        public DDTracer.DDSpanBuilder addReference(String referenceType, SpanContext spanContext) {
            throw new UnsupportedOperationException();
        }

        public DDTracer.DDSpanBuilder withTag(String tag, Number number) {
            return withTag(tag, (Object) number);
        }

        public DDTracer.DDSpanBuilder withTag(String tag, String string) {
            return withTag(tag, (Object) string);
        }

        public DDTracer.DDSpanBuilder withTag(String tag, boolean bool) {
            return withTag(tag, (Object) bool);
        }

        private DDTracer.DDSpanBuilder withTag(String tag, Object value) {
            this.tags.put(tag, value);
            return this;
        }

        public DDTracer.DDSpanBuilder withStartTimestamp(long timestampMillis) {
            this.timestamp = timestampMillis;
            return this;
        }

        public DDTracer.DDSpanBuilder withServiceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        public DDTracer.DDSpanBuilder withResourceName(String resourceName) {
            this.resourceName = resourceName;
            return this;
        }

        public DDTracer.DDSpanBuilder withErrorFlag() {
            this.errorFlag = true;
            return this;
        }

        public DDTracer.DDSpanBuilder withSpanType(String spanType) {
            this.spanType = spanType;
            return this;
        }


        public Span start() {

            // build the context
            DDSpanContext context = buildTheSpanContext();
            logger.startNewSpan(this.operationName, context.getSpanId());

            List<Span> trace = null;
            if (this.parent != null) {
                trace = parent.getTrace();
            }

            return new DDSpan(
                    DDTracer.this,
                    this.operationName,
                    trace,
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
                        Optional.ofNullable(this.serviceName).orElse(p.getServiceName()),
                        Optional.ofNullable(this.resourceName).orElse(this.operationName),
                        p.getBaggageItems(),
                        errorFlag,
                        null,
                        Optional.ofNullable(this.spanType).orElse(p.getSpanType()),
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
        return Math.abs(UUID.randomUUID().getMostSignificantBits());
    }
}
