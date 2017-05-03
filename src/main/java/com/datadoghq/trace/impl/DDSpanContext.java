package com.datadoghq.trace.impl;


import java.util.*;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.opentracing.Span;

/**
 * SpanContext represents Span state that must propagate to descendant Spans and across process boundaries.
 * <p>
 * SpanContext is logically divided into two pieces: (1) the user-level "Baggage" that propagates across Span
 * boundaries and (2) any Datadog fields that are needed to identify or contextualize
 * the associated Span instance
 */
public class DDSpanContext implements io.opentracing.SpanContext {

    // Opentracing attributes
    private final long traceId;
    private final long spanId;
    private final long parentId;
    private Map<String, String> baggageItems;

    // DD attributes
    /**
     * The service name is required, otherwise the span are dropped by the agent
     */
    private String serviceName;
    /**
     * The resource associated to the service (server_web, database, etc.)
     */
    private String resourceName;
    /**
     * True indicates that the span reports an error
     */
    private final boolean errorFlag;
    /**
     * The type of the span. If null, the Datadog Agent will report as a custom
     */
    private String spanType;
    /**
     * The collection of all span related to this one
     */
    private final List<Span> trace;
    /**
     * Each span have an operation name describing the current span
     */
    private String operationName;
    /**
     * Tags are associated to the current span, they will not propagate to the children span
     */
    private Map<String, Object> tags;
    // Others attributes
    /**
     * For technical reasons, the ref to the original tracer
     */
    private final DDTracer tracer;

    public DDSpanContext(
            long traceId,
            long spanId,
            long parentId,
            String serviceName,
            String operationName,
            String resourceName,
            Map<String, String> baggageItems,
            boolean errorFlag,
            String spanType,
            Map<String, Object> tags,
            List<Span> trace,
            DDTracer tracer) {

        this.traceId = traceId;
        this.spanId = spanId;
        this.parentId = parentId;

        if (baggageItems == null) {
            this.baggageItems = Collections.emptyMap();
        } else {
            this.baggageItems = baggageItems;
        }

        this.serviceName = serviceName;
        this.operationName = operationName;
        this.resourceName = resourceName;
        this.errorFlag = errorFlag;
        this.spanType = spanType;

        this.tags = tags;

        if (trace == null) {
            this.trace = new ArrayList<Span>();
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
        if (this.baggageItems.isEmpty()) {
            this.baggageItems = new HashMap<String, String>();
        }
        this.baggageItems.put(key, value);
    }

    public String getBaggageItem(String key) {
        return this.baggageItems.get(key);
    }

    public Map<String, String> getBaggageItems() {
        return baggageItems;
    }

    /* (non-Javadoc)
     * @see io.opentracing.SpanContext#baggageItems()
     */
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

    /**
     * Add a tag to the span. Tags are not propagated to the children
     *
     * @param tag   the tag-name
     * @param value the value of the value
     * @return the builder instance
     */
    public void setTag(String tag, Object value) {
        if (this.tags.isEmpty()) {
            this.tags = new HashMap<String, Object>();
        }
        this.tags.put(tag, value);
    }

    @Override
    public String toString() {
        return "Span [traceId=" + traceId
                + ", spanId=" + spanId
                + ", parentId=" + parentId + "]";
    }

    public void setOperationName(String operationName) {
        this.operationName = operationName;
    }

    public String getOperationName() {
        return operationName;
    }

    public Map<String, Object> getTags() {
        return tags;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }

    public void setType(String type) {
        this.spanType = type;
    }
}
