package com.datadoghq.trace.impl;


import java.util.Map;

public class SpanContext implements io.opentracing.SpanContext {
    public Iterable<Map.Entry<String, String>> baggageItems() {
        return null;
    }
}
