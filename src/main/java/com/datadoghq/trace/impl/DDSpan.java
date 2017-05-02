package com.datadoghq.trace.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import io.opentracing.Span;

/**
 * Represents an in-flight span in the opentracing system.
 * <p>
 * <p>Spans are created by the {@link DDTracer#buildSpan}.
 * This implementation adds some features according to the DD agent.
 */
public class DDSpan implements io.opentracing.Span {

    /**
     * Each span have an operation name describing the current span
     */
    private String operationName;
    /**
     * Tags are associated to the current span, they will not propagate to the children span
     */
    private Map<String, Object> tags;
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
     * @param operationName  the operation name associated to the span
     * @param tags           Tags attached to the span
     * @param timestampMicro if set, use this time instead of the auto-generated time
     * @param context        the context
     */
    protected DDSpan(
            String operationName,
            Map<String, Object> tags,
            long timestampMicro,
            DDSpanContext context) {

        this.operationName = operationName;
        this.tags = tags;
        this.context = context;

        // record the start time in nano (current milli + nano delta)
        if (timestampMicro == 0L) {
            this.startTimeMicro = System.currentTimeMillis() * 1000L;
        } else {
            this.startTimeMicro = timestampMicro;
        }
        this.startTimeNano = System.nanoTime();

        // track each span of the trace
        this.context.getTrace().add(this);

        // check DD attributes required
        if (this.context.getServiceName() == null) {
            throw new IllegalArgumentException("No ServiceName provided");
        }
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
        this.durationNano = stoptimeMicros * 1000L - this.startTimeMicro * 1000L;
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
        return setTag(tag, (Object) value);
    }

    /* (non-Javadoc)
     * @see io.opentracing.Span#setTag(java.lang.String, boolean)
     */
    public Span setTag(String tag, boolean value) {
        return setTag(tag, (Object) value);
    }

    /* (non-Javadoc)
     * @see io.opentracing.Span#setTag(java.lang.String, java.lang.Number)
     */
    public Span setTag(String tag, Number value) {
        return this.setTag(tag, (Object) value);
    }

    /**
     * Add a tag to the span. Tags are not propagated to the children
     *
     * @param tag   the tag-name
     * @param value the value of the value
     * @return the builder instance
     */
    private Span setTag(String tag, Object value) {
        tags.put(tag, value);
        return this;
    }

    public Span log(Map<String, ?> map) {
        return null;
    }

    public Span log(long l, Map<String, ?> map) {
        return null;
    }

    public Span log(String s) {
        return null;
    }

    public Span log(long l, String s) {
        return null;
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
        // FIXME operationName is in each constructor --> always IAE
        if (this.operationName != null) {
            throw new IllegalArgumentException("The operationName is already assigned.");
        }
        this.operationName = operationName;
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
    @JsonGetter("name")
    public String getOperationName() {
        return operationName;
    }

    @JsonIgnore
    public Map<String, Object> getTags() {
        return this.tags;
    }

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

    @JsonGetter
    public String getService() {
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
        return context.getResourceName() == null ? this.operationName : context.getResourceName();
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

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((context == null) ? 0 : context.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        DDSpan other = (DDSpan) obj;
        if (context == null) {
            if (other.context != null)
                return false;
        } else if (!context.equals(other.context))
            return false;
        return true;
    }
}
