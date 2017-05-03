package com.datadoghq.trace.impl;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.opentracing.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

/**
 * Represents an in-flight span in the opentracing system.
 * <p>
 * <p>Spans are created by the {@link DDTracer#buildSpan}.
 * This implementation adds some features according to the DD agent.
 */
public class DDSpan implements io.opentracing.Span {


    /**
     * StartTime stores the creation time of the span in milliseconds
     */
    private long startTimeMicro;
    /**
     * StartTimeNano stores the only the nanoseconds for more accuracy
     */
    private long startTimeNano;
    /**
     * The duration in nanoseconds computed using the startTimeMicro and startTimeNano
     */
    private long durationNano;
    /**
     * The context attached to the span
     */
    private final DDSpanContext context;

    private final static Logger logger = LoggerFactory.getLogger(DDSpan.class);

    /**
     * A simple constructor.
     * Currently, users have
     *
     * @param timestampMicro if set, use this time instead of the auto-generated time
     * @param context        the context
     */
    protected DDSpan(
            long timestampMicro,
            DDSpanContext context) {

        this.context = context;

        // record the start time in nano (current milli + nano delta)
        if (timestampMicro == 0L) {
            this.startTimeMicro = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());
        } else {
            this.startTimeMicro = timestampMicro;
        }
        this.startTimeNano = System.nanoTime();

        // track each span of the trace
        this.context.getTrace().add(this);

    }

    /* (non-Javadoc)
     * @see io.opentracing.Span#finish()
     */
    public void finish() {
        this.durationNano = System.nanoTime() - startTimeNano;
        afterFinish();
    }

    /* (non-Javadoc)
     * @see io.opentracing.Span#finish(long)
     */
    public void finish(long stoptimeMicros) {
        this.durationNano = TimeUnit.MICROSECONDS.toNanos(stoptimeMicros - this.startTimeMicro);
        afterFinish();
    }

    /**
     * Close the span. If the current span is the parent, check if each child has also been closed
     * If not, warned it
     */
    protected void afterFinish() {
        logger.debug("{} - Closing the span.", this);

        // warn if one of the parent's children is not finished
        if (this.isRootSpan()) {
            logger.debug("{} - The current span is marked as a root span", this);
            List<Span> spans = this.context.getTrace();
            logger.debug("{} - Checking {} children attached to the current span", this, spans.size());

            for (Span span : spans) {
                if (((DDSpan) span).getDurationNano() == 0L) {
                    logger.warn("{} - The parent span is marked as finished but this span isn't. You have to close each children.", this);
                }
            }
            this.context.getTracer().write(this.context.getTrace());
            logger.debug("{} - Sending the trace to the writer", this);
        }
    }

    /* (non-Javadoc)
     * @see io.opentracing.Span#close()
     */
    public void close() {
        this.finish();
    }

    /**
     * Check if the span is the root parent. It means that the traceId is the same as the spanId
     *
     * @return true if root, false otherwise
     */
    private boolean isRootSpan() {
        return context.getTraceId() == context.getSpanId();
    }

    /* (non-Javadoc)
     * @see io.opentracing.Span#setTag(java.lang.String, java.lang.String)
     */
    public Span setTag(String tag, String value) {
        this.context().setTag(tag, (Object) value);
        return this;
    }

    /* (non-Javadoc)
     * @see io.opentracing.Span#setTag(java.lang.String, boolean)
     */
    public Span setTag(String tag, boolean value) {
        this.context().setTag(tag, (Object) value);
        return this;
    }

    /* (non-Javadoc)
     * @see io.opentracing.Span#setTag(java.lang.String, java.lang.Number)
     */
    public Span setTag(String tag, Number value) {
         this.context().setTag(tag, (Object) value);
         return this;
    }


    /* (non-Javadoc)
     * @see io.opentracing.Span#context()
     */
    public DDSpanContext context() {
        return this.context;
    }

    /* (non-Javadoc)
     * @see io.opentracing.Span#setBaggageItem(java.lang.String, java.lang.String)
     */
    public Span setBaggageItem(String key, String value) {
        this.context.setBaggageItem(key, value);
        return this;
    }

    /* (non-Javadoc)
     * @see io.opentracing.Span#getBaggageItem(java.lang.String)
     */
    public String getBaggageItem(String key) {
        return this.context.getBaggageItem(key);
    }

    /* (non-Javadoc)
     * @see io.opentracing.Span#setOperationName(java.lang.String)
     */
    public Span setOperationName(String operationName) {
        this.context().setOperationName(operationName);
        return this;
    }

    /* (non-Javadoc)
     * @see io.opentracing.Span#log(java.lang.String, java.lang.Object)
     */
    public Span log(Map<String, ?> map) {
        logger.debug("`log` method is not implemented. Doing nothing");
        return this;
    }

    /* (non-Javadoc)
     * @see io.opentracing.Span#log(java.lang.String, java.lang.Object)
     */
    public Span log(long l, Map<String, ?> map) {
        logger.debug("`log` method is not implemented. Doing nothing");
        return this;
    }

    /* (non-Javadoc)
     * @see io.opentracing.Span#log(java.lang.String, java.lang.Object)
     */
    public Span log(String s) {
        logger.debug("`log` method is not implemented. Doing nothing");
        return this;
    }

    /* (non-Javadoc)
     * @see io.opentracing.Span#log(java.lang.String, java.lang.Object)
     */
    public Span log(long l, String s) {
        logger.debug("`log` method is not implemented. Doing nothing");
        return this;
    }

    /* (non-Javadoc)
     * @see io.opentracing.Span#log(java.lang.String, java.lang.Object)
     */
    public Span log(String s, Object o) {
        logger.debug("`log` method is not implemented. Doing nothing");
        return this;
    }

    /* (non-Javadoc)
     * @see io.opentracing.Span#log(long, java.lang.String, java.lang.Object)
     */
    public Span log(long l, String s, Object o) {
        logger.debug("`log` method is not implemented. Doing nothing");
        return this;
    }


    //Getters and JSON serialisation instructions

    /**
     * Meta merges baggage and tags (stringified values)
     *
     * @return merged context baggage and tags
     */
    @JsonGetter
    public Map<String, String> getMeta() {
        Map<String, String> meta = new HashMap<String, String>();
        for (Entry<String, String> entry : context().getBaggageItems().entrySet()) {
            meta.put(entry.getKey(), entry.getValue());
        }
        for (Entry<String, Object> entry : getTags().entrySet()) {
            meta.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        return meta;
    }

    @JsonGetter("start")
    public long getStartTime() {
        return startTimeMicro * 1000L;
    }

    @JsonGetter("duration")
    public long getDurationNano() {
        return durationNano;
    }

    @JsonGetter("service")
    public String getServiceName() {
        return context.getServiceName();
    }

    @JsonGetter("trace_id")
    public long getTraceId() {
        return context.getTraceId();
    }

    @JsonGetter("span_id")
    public long getSpanId() {
        return context.getSpanId();
    }

    @JsonGetter("parent_id")
    public long getParentId() {
        return context.getParentId();
    }

    @JsonGetter("resource")
    public String getResourceName() {
        return context.getResourceName() == null ? context.getOperationName() : context.getResourceName();
    }

    @JsonGetter("name")
    public String getOperationName() {
        return this.context().getOperationName();
    }

    @JsonIgnore
    public Map<String, Object> getTags() {
        return this.context().getTags();
    }
    @JsonGetter
    public String getType() {
        return context.getSpanType();
    }

    @JsonGetter
    public int getError() {
        return context.getErrorFlag() ? 1 : 0;
    }

    @Override
    public String toString() {
        return context.toString();
    }


    public Span setServiceName(String serviceName) {
        this.context().setServiceName(serviceName);
        return this;
    }

    public Span setResourceName(String resourceName) {
        this.context().setResourceName(resourceName);
        return this;
    }

    public Span setType(String type) {
        this.context().setType(type);
        return this;
    }
}
