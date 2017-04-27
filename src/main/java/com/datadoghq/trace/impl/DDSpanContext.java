package com.datadoghq.trace.impl;


import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DDSpanContext implements io.opentracing.SpanContext {

    // Opentracing attributes
    private final long traceId;
    private final long spanId;
    private final long parentId;
    private final Map<String, String> baggageItems;
    // DD attributes
    private final String serviceName;
    private final String resourceName;
    private final boolean errorFlag;
    private final String spanType;
    private final List<DDSpan> trace;
    // Others attributes
    private DDTracer tracer;


    public DDSpanContext(
            long traceId,
            long spanId,
            long parentId,
            String serviceName,
            String resourceName,
            Map<String, String> baggageItems,
            boolean errorFlag,
            String spanType,
            List<DDSpan> trace,
            DDTracer tracer) {

        this.traceId = traceId;
        this.spanId = spanId;
        this.parentId = parentId;

        if (baggageItems == null) {
            this.baggageItems = new HashMap<String, String>();
        } else {
            this.baggageItems = baggageItems;
        }
        this.serviceName = serviceName;
        this.resourceName = resourceName;
        this.errorFlag = errorFlag;
        this.spanType = spanType;

        if (trace == null) {
            this.trace = new ArrayList<DDSpan>();
        } else {
            this.trace = trace;
        }

        this.tracer = tracer;
    }

    public long getTraceId() {
        return this.traceId;
    }

    public long getParentId() {
        return this.parentId;
    }

    public long getSpanId() {
        return this.spanId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getResourceName() {
        return resourceName;
    }

    public boolean getErrorFlag() {
        return errorFlag;
    }


    public String getSpanType() {
        return spanType;
    }

    public void setBaggageItem(String key, String value) {
        this.baggageItems.put(key, value);
    }

    public String getBaggageItem(String key) {
        return this.baggageItems.get(key);
    }

    public Map<String, String> getBaggageItems() {
        return baggageItems;
    }

    public Iterable<Map.Entry<String, String>> baggageItems() {
        return this.baggageItems.entrySet();
    }

    @JsonIgnore
    public List<DDSpan> getTrace() {
        return this.trace;
    }

    @JsonIgnore
    public DDTracer getTracer() {
        return this.tracer;
    }

    @Override
	public String toString() {
		return "Span [traceId=" + traceId 
				+ ", spanId=" + spanId 
				+ ", parentId=" + parentId +"]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (spanId ^ (spanId >>> 32));
		result = prime * result + (int) (traceId ^ (traceId >>> 32));
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
		DDSpanContext other = (DDSpanContext) obj;
		if (spanId != other.spanId)
			return false;
        return traceId == other.traceId;
    }
}
