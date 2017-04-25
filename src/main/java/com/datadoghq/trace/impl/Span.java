package com.datadoghq.trace.impl;

import io.opentracing.SpanContext;

import java.util.Map;

public class Span implements io.opentracing.Span {
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
}
