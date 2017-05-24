package com.datadoghq.trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datadoghq.trace.integration.DDSpanContextDecorator;
import com.datadoghq.trace.propagation.Codec;
import com.datadoghq.trace.propagation.HTTPCodec;
import com.datadoghq.trace.sampling.AllSampler;
import com.datadoghq.trace.sampling.Sampler;
import com.datadoghq.trace.writer.DDAgentWriter;
import com.datadoghq.trace.writer.Writer;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;


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

    /**
     * Default service name if none provided on the trace or span
     */
    private final String defaultServiceName;

    /**
     * Span context decorators
     */
    private final List<DDSpanContextDecorator> spanContextDecorators = new ArrayList<DDSpanContextDecorator>();


    private final static Logger logger = LoggerFactory.getLogger(DDTracer.class);
    private final CodecRegistry registry;

    public static final String UNASSIGNED_DEFAULT_SERVICE_NAME = "unnamed-java-app";
    public static final Writer UNASSIGNED_WRITER = new DDAgentWriter();
    public static final Sampler UNASSIGNED_SAMPLER = new AllSampler();

    /**
     * Default constructor, trace/spans are logged, no trace/span dropped
     */
    public DDTracer() {
        this(UNASSIGNED_WRITER);
    }

    public DDTracer(Writer writer) {
        this(writer, new AllSampler());
    }

    public DDTracer(Writer writer, Sampler sampler) {
        this(UNASSIGNED_DEFAULT_SERVICE_NAME, writer, sampler);
    }

    public DDTracer(String defaultServiceName, Writer writer, Sampler sampler) {
        this.defaultServiceName = defaultServiceName;
        this.writer = writer;
        this.writer.start();
        this.sampler = sampler;
        registry = new CodecRegistry();
        registry.register(Format.Builtin.HTTP_HEADERS, new HTTPCodec());
    }

    /**
     * Returns the list of span context decorators
     *
     * @return the list of span context decorators
     */
    public List<DDSpanContextDecorator> getSpanContextDecorators() {
        return Collections.unmodifiableList(spanContextDecorators);
    }

    /**
     * Add a new decorator in the list ({@link DDSpanContextDecorator})
     *
     * @param decorator The decorator in the list
     */
    public void addDecorator(DDSpanContextDecorator decorator) {
        spanContextDecorators.add(decorator);
    }

    public DDSpanBuilder buildSpan(String operationName) {
        return new DDSpanBuilder(operationName);
    }


    public <T> void inject(SpanContext spanContext, Format<T> format, T carrier) {

        Codec<T> codec = registry.get(format);
        if (codec == null) {
            logger.warn("Unsupported format for propagation - {}", format.getClass().getName());
        } else {
            codec.inject((DDSpanContext) spanContext, carrier);
        }
    }

    public <T> SpanContext extract(Format<T> format, T carrier) {

        Codec<T> codec = registry.get(format);
        if (codec == null) {
            logger.warn("Unsupported format for propagation - {}", format.getClass().getName());
        } else {
            return codec.extract(carrier);
        }
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

    public void close() {
        writer.close();
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
            if (tag.equals(DDTags.SERVICE_NAME)) {
                return withServiceName(string);
            } else if (tag.equals(DDTags.RESOURCE_NAME)) {
                return withResourceName(string);
            } else if (tag.equals(DDTags.SPAN_TYPE)) {
                return withSpanType(string);
            } else {
                return withTag(tag, (Object) string);
            }
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
            return asChildOf(span==null? null : span.context());
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
            if (this.tags.isEmpty()) {
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
            if (serviceName == null) {
                if (p != null && p.getServiceName() != null) {
                    serviceName = p.getServiceName();
                } else {
                    serviceName = defaultServiceName;
                }
            }
            
            String operationName = this.operationName != null ? this.operationName : this.resourceName;
            
            //this.operationName, this.tags,

            // some attributes are inherited from the parent
            context = new DDSpanContext(
                    this.parent == null ? generatedId : p.getTraceId(),
                    generatedId,
                    this.parent == null ? 0L : p.getSpanId(),
                    serviceName,
                    operationName,
                    this.resourceName,
                    this.parent == null ? new HashMap<String, String>() : p.getBaggageItems(),
                    errorFlag,
                    spanType,
                    this.tags,
                    this.parent == null ? null : p.getTrace(),
                    DDTracer.this
            );

            return context;
        }

    }

    private static class CodecRegistry {

        private final Map<Format<?>, Codec<?>> codecs = new HashMap<Format<?>, Codec<?>>();

        @SuppressWarnings("unchecked")
		<T> Codec<T> get(Format<T> format) {
            return (Codec<T>) codecs.get(format);
        }

        public <T> void register(Format<T> format, Codec<T> codec) {
            codecs.put(format, codec);
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
