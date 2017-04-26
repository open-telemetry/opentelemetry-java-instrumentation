package com.datadoghq.trace.impl;


import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class DDSpanContext implements io.opentracing.SpanContext {

    // Public span attributes
    private final String serviceName;
    private final String resourceName;
    private final long traceId;
    private final long spanId;
    private final long parentId;
    private final Map<String, String> baggageItems; // know as 'meta' in dd-trace-py
    private final boolean errorFlag;
    private final Map<String, Object> metrics;
    private final String spanType;
    // Sampler attributes
    private boolean sampled;

    // Testing purpose, @todo better mock
    DDSpanContext() {
        serviceName = null;
        resourceName = null;
        traceId = 0;
        spanId = 0;
        parentId = 0;
        baggageItems = new HashMap<>();
        errorFlag = false;
        metrics = null;
        spanType = null;
    }

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
            boolean sampled) {        this.traceId = traceId;
        this.spanId = spanId;
        this.parentId = parentId;
        Optional<Map<String, String>> baggage = Optional.ofNullable(baggageItems);
        this.serviceName = serviceName;
        this.resourceName = resourceName;
        this.baggageItems = baggage.orElse(new HashMap<>());
        this.errorFlag = errorFlag;
        this.metrics = metrics;
        this.spanType = spanType;
        this.sampled = sampled;
    }


    public Iterable<Map.Entry<String, String>> baggageItems() {
        return this.baggageItems.entrySet();
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
}
