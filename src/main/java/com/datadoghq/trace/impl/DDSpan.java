package com.datadoghq.trace.impl;

import io.opentracing.SpanContext;

import java.util.Map;
import java.util.Optional;


public class DDSpan implements io.opentracing.Span {

    private final Tracer tracer;
    private String operationName;
    private Map<String, Object> tags;
    private long startTime;
    private long durationNano;
    private final DDSpanContext context;

    DDSpan(
            Tracer tracer,
            String operationName,
            Map<String, Object> tags,
            Long timestamp,
            DDSpanContext context) {
        this.tracer = tracer;
        this.operationName = operationName;
        this.tags = tags;
        this.startTime = Optional.ofNullable(timestamp).orElse(System.nanoTime());
        this.context = context;
    }

    public SpanContext context() {
        return this.context;
    }

    public void finish() {
        this.durationNano = System.nanoTime() - startTime;
    }

    public void finish(long nano) {
        this.durationNano = nano;
    }

    public void close() {
        this.finish();
    }

    public io.opentracing.Span setTag(String tag, String value) {
        return this.setTag(tag, value);
    }

    public io.opentracing.Span setTag(String tag, boolean value) {
        return this.setTag(tag, value);
    }

    public io.opentracing.Span setTag(String tag, Number value) {
        return this.setTag(tag, (Object) value);
    }

    private io.opentracing.Span setTag(String tag, Object value) {
        this.tags.put(tag, value);
        return this;
    }

    public io.opentracing.Span log(Map<String, ?> map) {
        return null;
    }

    public io.opentracing.Span log(long l, Map<String, ?> map) {
        return null;
    }

    public io.opentracing.Span log(String s) {
        return null;
    }

    public io.opentracing.Span log(long l, String s) {
        return null;
    }

    public io.opentracing.Span setBaggageItem(String key, String value) {
        this.context.setBaggageItem(key, value);
        return this;
    }

    public String getBaggageItem(String key) {
        return this.context.getBaggageItem(key);
    }

    public io.opentracing.Span setOperationName(String operationName) {
        this.operationName = operationName;
        return this;
    }

    public io.opentracing.Span log(String s, Object o) {
        return null;
    }

    public io.opentracing.Span log(long l, String s, Object o) {
        return null;
    }

    public String getOperationName() {
        return operationName;
    }

    public Map<String, Object> getTags() {
        return this.tags;
    }

    public long getStartTime() {
        return startTime;
    }

    public DDSpanContext getContext() {
        return context;
    }

    public DDSpanContext DDContext() {
        return this.context;
    }
}
