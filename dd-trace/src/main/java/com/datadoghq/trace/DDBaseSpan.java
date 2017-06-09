package com.datadoghq.trace;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import io.opentracing.BaseSpan;

@SuppressWarnings("rawtypes")
public abstract class DDBaseSpan<S extends BaseSpan> implements BaseSpan<S> {

	 /**
     * StartTime stores the creation time of the span in milliseconds
     */
    protected long startTimeMicro;
    /**
     * StartTimeNano stores the only the nanoseconds for more accuracy
     */
    protected long startTimeNano;
    /**
     * The duration in nanoseconds computed using the startTimeMicro and startTimeNano
     */
    protected long durationNano;
    /**
     * The context attached to the span
     */
    protected final DDSpanContext context;

    private final static Logger logger = LoggerFactory.getLogger(DDBaseSpan.class);

    /**
     * A simple constructor.
     * Currently, users have
     *
     * @param timestampMicro if set, use this time instead of the auto-generated time
     * @param context        the context
     */
    protected DDBaseSpan(
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

    public final void finish() {
        this.durationNano = System.nanoTime() - startTimeNano;
        afterFinish();
    }

    public final void finish(long stoptimeMicros) {
        this.durationNano = TimeUnit.MICROSECONDS.toNanos(stoptimeMicros - this.startTimeMicro);
        afterFinish();
    }

    /**
     * Close the span. If the current span is the parent, check if each child has also been closed
     * If not, warned it
     */
    protected final void afterFinish() {
        logger.debug("{} - Closing the span.", this);

        // warn if one of the parent's children is not finished
        if (this.isRootSpan()) {
            List<DDBaseSpan<?>> spans = this.context().getTrace();

            for (DDBaseSpan<?> span : spans) {
                if (((DDBaseSpan<?>) span).getDurationNano() == 0L) {
                    logger.warn("{} - The parent span is marked as finished but this span isn't. You have to close each children.", this);
                }
            }
            this.context.getTracer().write(this.context.getTrace());
            logger.debug("{} - Write the trace", this);
        }
    }

    /**
     * Check if the span is the root parent. It means that the traceId is the same as the spanId
     *
     * @return true if root, false otherwise
     */
    protected final boolean isRootSpan() {

        if (context().getTrace().isEmpty()) {
            return false;
        }
        // First item of the array AND tracer set
        DDBaseSpan<?> first = context().getTrace().get(0);
        return first.context().getSpanId() == this.context().getSpanId() && this.context.getTracer() != null;
    }


    /* (non-Javadoc)
     * @see io.opentracing.BaseSpan#setTag(java.lang.String, java.lang.String)
     */
    @Override
    public final S setTag(String tag, String value) {
        this.context().setTag(tag, (Object) value);
        return thisInstance();
    }


    /* (non-Javadoc)
     * @see io.opentracing.BaseSpan#setTag(java.lang.String, boolean)
     */
    @Override
    public final S setTag(String tag, boolean value) {
        this.context().setTag(tag, (Object) value);
        return thisInstance();
    }


    /* (non-Javadoc)
     * @see io.opentracing.BaseSpan#setTag(java.lang.String, java.lang.Number)
     */
    @Override
    public final S setTag(String tag, Number value) {
         this.context().setTag(tag, (Object) value);
         return thisInstance();
    }


    /* (non-Javadoc)
     * @see io.opentracing.BaseSpan#context()
     */
    @Override
    public final DDSpanContext context() {
        return this.context;
    }

    /* (non-Javadoc)
     * @see io.opentracing.BaseSpan#getBaggageItem(java.lang.String)
     */
    @Override
    public final String getBaggageItem(String key) {
        return this.context.getBaggageItem(key);
    }
    
    /* (non-Javadoc)
     * @see io.opentracing.BaseSpan#setBaggageItem(java.lang.String, java.lang.String)
     */
    @Override
    public final S setBaggageItem(String key, String value) {
        this.context.setBaggageItem(key, value);
        return thisInstance();
    }

    /* (non-Javadoc)
     * @see io.opentracing.BaseSpan#setOperationName(java.lang.String)
     */
    @Override
    public final S setOperationName(String operationName) {
        this.context().setOperationName(operationName);
        return thisInstance();
    }

    /* (non-Javadoc)
     * @see io.opentracing.BaseSpan#log(java.util.Map)
     */
    @Override
    public final S log(Map<String, ?> map) {
        logger.debug("`log` method is not implemented. Doing nothing");
        return thisInstance();
    }

    /* (non-Javadoc)
     * @see io.opentracing.BaseSpan#log(long, java.util.Map)
     */
    @Override
    public final S log(long l, Map<String, ?> map) {
        logger.debug("`log` method is not implemented. Doing nothing");
        return thisInstance();
    }

    /* (non-Javadoc)
     * @see io.opentracing.BaseSpan#log(java.lang.String)
     */
    @Override
    public final S log(String s) {
        logger.debug("`log` method is not implemented. Provided log: {}",s);
        return thisInstance();
    }

    /* (non-Javadoc)
     * @see io.opentracing.BaseSpan#log(long, java.lang.String)
     */
    @Override
    public final S log(long l, String s) {
        logger.debug("`log` method is not implemented. Provided log: {}",s);
        return thisInstance();
    }


    /* (non-Javadoc)
     * @see io.opentracing.BaseSpan#log(java.lang.String, java.lang.Object)
     */
    @Override
    public final S log(String s, Object o) {
        logger.debug("`log` method is not implemented. Provided log: {}",s);
        return thisInstance();
    }

    /* (non-Javadoc)
     * @see io.opentracing.BaseSpan#log(long, java.lang.String, java.lang.Object)
     */
    @Override
    public final S log(long l, String s, Object o) {
        logger.debug("`log` method is not implemented. Provided log: {}",s);
        return thisInstance();
    }

    public final S setServiceName(String serviceName) {
        this.context().setServiceName(serviceName);
        return thisInstance();
    }

    public final S setResourceName(String resourceName) {
        this.context().setResourceName(resourceName);
        return thisInstance();
    }

    public final S setSpanType(String type) {
        this.context().setSpanType(type);
        return thisInstance();
    }
    
    protected abstract S thisInstance();

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
        return context.getResourceName();
    }

    @JsonGetter("name")
    public String getOperationName() {
        return context.getOperationName();
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
}
