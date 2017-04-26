package com.datadoghq.trace.impl;

import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonGetter;

import io.opentracing.Span;
import io.opentracing.SpanContext;


public class DDSpan implements io.opentracing.Span {

    private final Tracer tracer;
    private final String operationName;
    private Map<String, Object> tags;
    private long startTime;
    private long durationMilliseconds;
    private final DDSpanContext context;

    DDSpan(
            Tracer tracer,
            String operationName,
            Map<String, Object> tags,
            Optional<Long> timestamp,
            DDSpanContext context) {
        this.tracer = tracer;
        this.operationName = operationName;
        this.tags = tags;
        this.startTime = timestamp.orElse(System.currentTimeMillis());
        this.context = context;
    }

    public SpanContext context() {
        return this.context;
    }

    public void finish() {

    }

    public void finish(long l) {

    }

    public void close() {

    }

    public Span setTag(String s, String s1) {
        return null;
    }

    public Span setTag(String s, boolean b) {
        return null;
    }

    public Span setTag(String s, Number number) {
        return null;
    }

    public Span log(Map<String, ?> map) {
        return null;
    }

    public Span log(long l, Map<String, ?> map) {
        return null;
    }

    public Span log(String s) {
        return null;
    }

    public Span log(long l, String s) {
        return null;
    }

    public Span setBaggageItem(String s, String s1) {
        return null;
    }

    public String getBaggageItem(String s) {
        return null;
    }

    public Span setOperationName(String s) {
        return null;
    }

    public Span log(String s, Object o) {
        return null;
    }

    public Span log(long l, String s, Object o) {
        return null;
    }

    //Getters and JSON serialisation instructions
    
    @JsonGetter(value="name")
    public String getOperationName() {
        return operationName;
    }
    
    @JsonGetter(value="meta")
    public Map<String, Object> getTags() {
        return this.tags;
    }

    @JsonGetter(value="start")
    public long getStartTime() {
        return startTime * 1000000;
    }
    
    @JsonGetter(value="duration")
    public long getDurationInNS(){
    	return durationMilliseconds * 1000000;
    }
    
    public String getService(){
    	return context.getServiceName();
    }
    
    @JsonGetter(value="trace_id")
    public long getTraceId(){
    	return context.getTraceId();
    }
    
    @JsonGetter(value="span_id")
    public long getSpanId(){
    	return context.getSpanId();
    }
    
    @JsonGetter(value="parent_id")
    public long getParentId(){
    	return context.getParentId();
    }
    
    @JsonGetter(value="resource")
    public String getResourceName(){
    	return context.getResourceName()==null?getOperationName():context.getResourceName();
    }
    
    public String getType(){
    	return context.getSpanType();
    }
    
    public int getError(){
    	return context.getErrorFlag()?1:0;
    }
    
}
