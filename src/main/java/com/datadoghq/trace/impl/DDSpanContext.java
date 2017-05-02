package com.datadoghq.trace.impl;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    protected long traceId;
    protected long spanId;
    protected long parentId;
    protected Map<String, String> baggageItems;

    // DD attributes
    /**
     * The service name is required, otherwise the span are dropped by the agent
     */
    protected String serviceName;
    /**
     * The resource associated to the service (server_web, database, etc.)
     */
    protected String resourceName;
    /**
     * True indicates that the span reports an error
     */
    protected boolean errorFlag;
    /**
     * The type of the span. If null, the Datadog Agent will report as a custom
     */
    protected String spanType;
    /**
     * The collection of all span related to this one
     */
    protected final List<Span> trace;
    // Others attributes
    /**
     * For technical reasons, the ref to the original tracer
     */
    protected DDTracer tracer;

    public DDSpanContext(
            long traceId,
            long spanId,
            long parentId,
            String serviceName,
            String resourceName,
            Map<String, String> baggageItems,
            boolean errorFlag,
            String spanType,
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
        this.spanType = spanType;

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

    @Override
    public String toString() {
        return "Span [traceId=" + traceId
                + ", spanId=" + spanId
                + ", parentId=" + parentId + "]";
    }

}
