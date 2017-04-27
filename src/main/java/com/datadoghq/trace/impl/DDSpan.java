package com.datadoghq.trace.impl;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import io.opentracing.Span;
import io.opentracing.SpanContext;


public class DDSpan implements io.opentracing.Span {

	protected final DDTracer tracer;
    protected String operationName;
    protected Map<String, Object> tags;
    protected long startTimeNano;
    protected long durationNano;
    protected final DDSpanContext context;
    protected final List<Span> trace;

    DDSpan(
            DDTracer tracer,
            String operationName,
            List<Span> trace,
            Map<String, Object> tags,
            Long timestampMilliseconds,
            DDSpanContext context) {

        this.tracer = tracer;
        this.operationName = operationName;
        this.trace = Optional.ofNullable(trace).orElse(new ArrayList<>());
        this.tags = tags;
        this.startTimeNano = Optional.ofNullable(timestampMilliseconds).orElse(Clock.systemUTC().millis()) * 1000000L;
        this.context = context;

        // track each span of the trace
        this.trace.add(this);

    }

    public SpanContext context() {
        return this.context;
    }

    public void finish() {
        finish(Clock.systemUTC().millis());
    }

    public void finish(long stopTimeMillis) {
        this.durationNano = (stopTimeMillis * 1000000L - startTimeNano) ;
        if (this.isRootSpan()) {
            this.trace.stream()
                    .filter(s -> {
                        boolean isSelf = ((DDSpanContext) s.context()).getSpanId() == ((DDSpanContext) this.context()).getSpanId();
                        boolean isFinished = ((DDSpan) s).getDurationNano() != 0L;
                        return !isSelf && !isFinished;
                    })
                    .forEach(Span::finish);
        }
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

    public Span setBaggageItem(String key, String value) {
        this.context.setBaggageItem(key, value);
        return this;
    }

    public String getBaggageItem(String key) {
        return this.context.getBaggageItem(key);
    }

    public Span setOperationName(String operationName) {
        // FIXME: @renaud, the operationName (mandatory) is always set by the constructor
        // FIXME: should be an UnsupportedOperation if we don't want to update the operationName + final
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
        return startTimeNano;
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
        return context.getResourceName() == null ? getOperationName() : context.getResourceName();
    }

    public String getType() {
        return context.getSpanType();
    }

    public int getError() {
        return context.getErrorFlag() ? 1 : 0;
    }

    @JsonIgnore
    public List<Span> getTrace() {
        return trace;
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
