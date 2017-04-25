package com.datadoghq.trace.impl;


import java.util.Map;

public class SpanContext implements io.opentracing.SpanContext {

    // Public span attributes
    private String serviceName;
    private String resourceName;
    private long spanId;
    private long traceId;
    private long parentId;
    private Map<String, String> baggageItems; // know as 'meta' in dd-trace-py
    private boolean errorFlag;
    private Map<String, Object> metrics;
    private String spanType;
    private long start;
    private long duration;
    // Sampler attributes
    private boolean sampled;


    public Iterable<Map.Entry<String, String>> baggageItems() {
        return null;
    }
}
