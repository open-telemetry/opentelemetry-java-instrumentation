package com.datadoghq.trace.impl;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonGetter;

import io.opentracing.Span;


public class DDSpan implements io.opentracing.Span {

    protected String operationName;
    protected Map<String, Object> tags;
    protected final long startTime;
    protected long startTimeNano;
    protected long durationNano;
    protected final DDSpanContext context;

    private final static Logger logger = LoggerFactory.getLogger(DDSpan.class);

    DDSpan(
            String operationName,
            Map<String, Object> tags,
            long timestampMilliseconds,
            DDSpanContext context) {

        this.operationName = operationName;
        this.tags = tags;
        this.context = context;

        // record the start time in nano (current milli + nano delta)
        if (timestampMilliseconds == 0L) {
            this.startTime = System.currentTimeMillis();
        } else {
            this.startTime = timestampMilliseconds;
        }
        this.startTimeNano = System.nanoTime();

        // track each span of the trace
        this.context.getTrace().add(this);

        // check DD attributes required
        if (this.context.getServiceName() == null) {
            throw new IllegalArgumentException("No ServiceName provided");
        }
    }

    public void finish(long stopTimeMillis) {

        // formula: millis(stop - start) * 1000 * 1000 + keepNano(nano(stop - start))
        long stopTimeNano = System.nanoTime();
        this.durationNano = (stopTimeMillis - startTime) * 1000000L + ((stopTimeNano - this.startTimeNano) % 1000000L);

        logger.debug(this + " - Closing the span." + this.toString());

        // warn if one of the parent's children is not finished
        if (this.isRootSpan()) {
            logger.debug(this + " - The current span is marked as a root span");
            List<Span> spans = this.context.getTrace();
            logger.debug(this + " - Checking " + spans.size() + " children attached to the current span");

            for (Span span : spans) {
                if (((DDSpan) span).getDurationNano() == 0L) {
                    // FIXME
                    logger.warn(this + " - The parent span is marked as finished but this span isn't. You have to close each children." + this.toString());
                }
            }
            this.context.getTracer().write(this.context.getTrace());
            logger.debug(this + " - Sending the trace to the writer");

        }

    }

    public void finish() {
        finish(System.currentTimeMillis());
    }

    public void close() {
        this.finish();
    }

    private boolean isRootSpan() {
        return context.getTraceId() == context.getSpanId();
    }

    public Span setTag(String tag, String value) {
        return this.setTag(tag, value);
    }

    public Span setTag(String tag, boolean value) {
        return this.setTag(tag, value);
    }

    public Span setTag(String tag, Number value) {
        return this.setTag(tag, (Object) value);
    }

    private Span setTag(String tag, Object value) {
        this.tags.put(tag, value);
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

    public DDSpanContext context() {
        return this.context;
    }

    public Span setBaggageItem(String key, String value) {
        this.context.setBaggageItem(key, value);
        return this;
    }

    public String getBaggageItem(String key) {
        return this.context.getBaggageItem(key);
    }

    public Span setOperationName(String operationName) {
        // FIXME operationName is in each constructor --> always IAE
        if (this.operationName != null) {
            throw new IllegalArgumentException("The operationName is already assigned.");
        }
        this.operationName = operationName;
        return this;
    }

    public Span log(String s, Object o) {
        return null;
    }

    public Span log(long l, String s, Object o) {
        return null;
    }

    //Getters and JSON serialisation instructions
    @JsonGetter(value = "name")
    public String getOperationName() {
        return operationName;
    }

    @JsonGetter(value = "meta")
    public Map<String, Object> getTags() {
        return this.tags;
    }

    @JsonGetter(value = "start")
    public long getStartTime() {
        return startTime * 1000000L;
    }

    @JsonGetter(value = "duration")
    public long getDurationNano() {
        return durationNano;
    }

    public String getService() {
        return context.getServiceName();
    }

    @JsonGetter(value = "trace_id")
    public long getTraceId() {
        return context.getTraceId();
    }

    @JsonGetter(value = "span_id")
    public long getSpanId() {
        return context.getSpanId();
    }

    @JsonGetter(value = "parent_id")
    public long getParentId() {
        return context.getParentId();
    }

    @JsonGetter(value = "resource")
    public String getResourceName() {
        return context.getResourceName() == null ? this.operationName : context.getResourceName();
    }

    public String getType() {
        return context.getSpanType();
    }

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
