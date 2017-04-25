package com.datadoghq.trace.impl;

import io.opentracing.SpanContext;

import java.util.Map;
import java.util.Optional;


public class Span implements io.opentracing.Span {


    private final String operationName;
    private Map<String, Object> tags;
    private long startTime;
    private final SpanContext parent;

    Span(
            String operationName,
            Map<String, Object> tags,
            Optional<Long> timestamp, SpanContext parent) {

        this.operationName = operationName;
        this.tags = tags;
        this.startTime = timestamp.orElse(System.nanoTime());
        this.parent = parent;
    }

    public SpanContext context() {
        return null;
    }

    public void finish() {

    }

    public void finish(long l) {

    }

    public void close() {

    }

    public io.opentracing.Span setTag(String s, String s1) {
        return null;
    }

    public io.opentracing.Span setTag(String s, boolean b) {
        return null;
    }

    public io.opentracing.Span setTag(String s, Number number) {
        return null;
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

    public io.opentracing.Span setBaggageItem(String s, String s1) {
        return null;
    }

    public String getBaggageItem(String s) {
        return null;
    }

    public io.opentracing.Span setOperationName(String s) {
        return null;
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

    public SpanContext getParent() {
        return parent;
    }
}
