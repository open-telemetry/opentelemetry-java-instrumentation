package com.datadoghq.trace.impl;

import com.datadoghq.trace.Sampler;
import com.datadoghq.trace.Writer;
import com.datadoghq.trace.writer.impl.LoggingWritter;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * DDTracer makes it easy to send traces and span to DD using the OpenTracing instrumentation.
 */
public class DDTracer implements io.opentracing.Tracer {

    /**
     * Writer is an charge of reporting traces and spans to the desired endpoint
     */
    private Writer writer;
    /**
     * Sampler defines the sampling policy in order to reduce the number of traces for instance
     */
    private final Sampler sampler;


    private final static Logger logger = LoggerFactory.getLogger(DDTracer.class);

    /**
     * Default constructor, trace/spans are logged, no trace/span dropped
     */
    public DDTracer() {
        this(new LoggingWritter(), new AllSampler());
    }

    public DDTracer(Writer writer, Sampler sampler) {
        this.writer = writer;
        this.sampler = sampler;
    }

    public DDSpanBuilder buildSpan(String operationName) {
        return new DDSpanBuilder(operationName);
    }

    public <C> void inject(SpanContext spanContext, Format<C> format, C c) {
        //FIXME Implement it ASAP
        logger.warn("Method `inject` not implemented yet");
    }

    public <C> SpanContext extract(Format<C> format, C c) {
        //FIXME Implement it ASAP
        logger.warn("Method `inject` not implemented yet");
        return null;
    }


    /**
     * We use the sampler to know if the trace has to be reported/written.
     * The sampler is called on the first span (root span) of the trace.
     * If the trace is marked as a sample, we report it.
     *
     * @param trace a list of the spans related to the same trace
     */
    public void write(List<Span> trace) {
        if (trace.isEmpty()) {
            return;
        }
        if (this.sampler.sample((DDSpan) trace.get(0))) {
            this.writer.write(trace);
        }
    }

    /**
     * Spans are built using this builder
     */
    public class DDSpanBuilder implements SpanBuilder {

        /**
         * Each span must have an operationName according to the opentracing specification
         */
        private String operationName;

        // Builder attributes
        private Map<String, Object> tags = Collections.emptyMap();
        private long timestamp;
        private SpanContext parent;
        private String serviceName;
        private String resourceName;
        private boolean errorFlag;
        private String spanType;

        /**
         * This method actually build the span according to the builder settings
         * DD-Agent requires a serviceName. If it has not been provided, the method will throw a RuntimeException
         *
         * @return An fresh span
         */
        public DDSpan start() {

            // build the context
            DDSpanContext context = buildSpanContext();
            DDSpan span = new DDSpan(this.timestamp, context);

            logger.debug("{} - Starting a new span.", span);

            return span;
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

        public DDSpanBuilder(String operationName) {
            this.operationName = operationName;
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

        public Iterable<Map.Entry<String, String>> baggageItems() {
            if (parent == null) {
                return Collections.emptyList();
            }
            return parent.baggageItems();
        }

        public DDTracer.DDSpanBuilder asChildOf(Span span) {
            return asChildOf(span.context());
        }

        public DDTracer.DDSpanBuilder asChildOf(SpanContext spanContext) {
            this.parent = spanContext;
            return this;
        }

        public DDTracer.DDSpanBuilder addReference(String referenceType, SpanContext spanContext) {
            logger.debug("`addReference` method is not implemented. Doing nothing");
            return this;
        }

        // Private methods
        private DDTracer.DDSpanBuilder withTag(String tag, Object value) {
            if (this.tags.isEmpty()){
                this.tags = new HashMap<String, Object>();
            }
            this.tags.put(tag, value);
            return this;
        }

        private long generateNewId() {
            return System.nanoTime();
        }

        /**
         * Build the SpanContext, if the actual span has a parent, the following attributes must be propagated:
         * - ServiceName
         * - Baggage
         * - Trace (a list of all spans related)
         * - SpanType
         *
         * @return the context
         */
        private DDSpanContext buildSpanContext() {
            long generatedId = generateNewId();
            DDSpanContext context;
            DDSpanContext p = this.parent != null ? (DDSpanContext) this.parent : null;

            String spanType = this.spanType;
            if (spanType == null && this.parent != null) {
                spanType = p.getSpanType();
            }

            String serviceName = this.serviceName;
            if (serviceName == null && this.parent != null) {
                serviceName = p.getServiceName();
            }

            //this.operationName, this.tags,

            // some attributes are inherited from the parent
            context = new DDSpanContext(
                    this.parent == null ? generatedId : p.getTraceId(),
                    generatedId,
                    this.parent == null ? 0L : p.getSpanId(),
                    serviceName,
                    this.operationName,
                    this.resourceName,
                    this.parent == null ? Collections.<String, String>emptyMap() : p.getBaggageItems(),
                    errorFlag,
                    spanType,
                    this.tags,
                    this.parent == null ? null : p.getTrace(),
                    DDTracer.this
            );

            logger.debug("Building a new span context. {}", context);
            return context;
        }

    }

    @Override
    public String toString() {
        return "DDTracer{" +
                "writer=" + writer +
                ", sampler=" + sampler +
                '}';
    }
}
