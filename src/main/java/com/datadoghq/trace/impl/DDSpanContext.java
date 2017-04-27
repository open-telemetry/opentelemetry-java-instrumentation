package com.datadoghq.trace.impl;


import com.fasterxml.jackson.annotation.JsonIgnore;
import io.opentracing.Span;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DDSpanContext implements io.opentracing.SpanContext {

    private static final String SPAN_TYPE_DEFAULT = "custom";
    // Opentracing attributes
    private final long traceId;
    private final long spanId;
    private final long parentId;
    private final Map<String, String> baggageItems;
    // DD attributes
    private final String serviceName;
    private final String resourceName;
    private final boolean errorFlag;
    private final Map<String, Object> metrics;
    private final String spanType;
    private final List<Span> trace;
    // Others attributes
    private boolean sampled;
    private DDTracer tracer;


    public DDSpanContext(
            long traceId,
            long spanId,
            long parentId,
            String serviceName,
            String resourceName,
            Map<String, String> baggageItems,
            boolean errorFlag,
            Map<String, Object> metrics,
            String spanType,
            boolean sampled,
            List<Span> trace,
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
        this.metrics = metrics;
        this.spanType = spanType;
        this.sampled = sampled;

        if (trace == null) {
            this.trace = new ArrayList<Span>();
        } else {
            this.trace = trace;
        }

        this.tracer = tracer;
    }

    protected static DDSpanContext newContext(long generateId, String serviceName, String resourceName) {
        DDSpanContext context = new DDSpanContext(
                // Opentracing attributes
                generateId, generateId, 0L,
                // DD attributes
                serviceName, resourceName,
                // Other stuff
                null, false, null,
                DDSpanContext.SPAN_TYPE_DEFAULT, true,
                null, null

        );
        return context;
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

    public Map<String, Object> getMetrics() {
        return metrics;
    }

    public String getSpanType() {
        return spanType;
    }

    public boolean getSampled() {
        return sampled;
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
    public List<Span> getTrace() {
        return this.trace;
    }

    @JsonIgnore
    public DDTracer getTracer() {
        return this.tracer;
    }

    @Override
    public String toString() {
        return "DDSpanContext{" +
                "traceId=" + traceId +
                ", spanId=" + spanId +
                ", parentId=" + parentId +
                ", baggageItems=" + baggageItems +
                ", serviceName='" + serviceName + '\'' +
                ", resourceName='" + resourceName + '\'' +
                ", errorFlag=" + errorFlag +
                ", metrics=" + metrics +
                ", spanType='" + spanType + '\'' +
                ", trace.size=" + trace.size() +
                ", sampled=" + sampled +
                ", tracer=" + tracer +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DDSpanContext context = (DDSpanContext) o;

        if (traceId != context.traceId) return false;
        if (spanId != context.spanId) return false;
        if (parentId != context.parentId) return false;
        if (errorFlag != context.errorFlag) return false;
        if (sampled != context.sampled) return false;
        if (baggageItems != null ? !baggageItems.equals(context.baggageItems) : context.baggageItems != null)
            return false;
        if (serviceName != null ? !serviceName.equals(context.serviceName) : context.serviceName != null) return false;
        if (resourceName != null ? !resourceName.equals(context.resourceName) : context.resourceName != null)
            return false;
        if (metrics != null ? !metrics.equals(context.metrics) : context.metrics != null) return false;
        if (spanType != null ? !spanType.equals(context.spanType) : context.spanType != null) return false;
        if (trace != null ? !trace.equals(context.trace) : context.trace != null) return false;
        return tracer != null ? tracer.equals(context.tracer) : context.tracer == null;
    }

    @Override
    public int hashCode() {
        int result = (int) (traceId ^ (traceId >>> 32));
        result = 31 * result + (int) (spanId ^ (spanId >>> 32));
        result = 31 * result + (int) (parentId ^ (parentId >>> 32));
        result = 31 * result + (baggageItems != null ? baggageItems.hashCode() : 0);
        result = 31 * result + (serviceName != null ? serviceName.hashCode() : 0);
        result = 31 * result + (resourceName != null ? resourceName.hashCode() : 0);
        result = 31 * result + (errorFlag ? 1 : 0);
        result = 31 * result + (metrics != null ? metrics.hashCode() : 0);
        result = 31 * result + (spanType != null ? spanType.hashCode() : 0);
        result = 31 * result + (trace != null ? trace.hashCode() : 0);
        result = 31 * result + (sampled ? 1 : 0);
        result = 31 * result + (tracer != null ? tracer.hashCode() : 0);
        return result;
    }
}
