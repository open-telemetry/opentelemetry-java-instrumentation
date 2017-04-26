package com.datadoghq.trace.impl;


import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public class SpanContext implements io.opentracing.SpanContext {

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

    public SpanContext(
            long traceId,
            long spanId,
            long parentId,
            String serviceName,
            String resourceName,
            Map<String, String> baggageItems,
            boolean errorFlag,
            Map<String, Object> metrics,
            String spanType,
            boolean sampled) {
        this.serviceName = serviceName;
        this.resourceName = resourceName;
        this.traceId = traceId;
        this.spanId = spanId;
        this.parentId = parentId;
        Optional<Map<String, String>> baggage = Optional.ofNullable(baggageItems);
        this.baggageItems = baggage.orElse(Collections.emptyMap());
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

    public boolean isErrorFlag() {
        return errorFlag;
    }

    public Map<String, Object> getMetrics() {
        return metrics;
    }

    public String getSpanType() {
        return spanType;
    }

    public boolean isSampled() {
        return sampled;
    }

}
