package com.datadoghq.trace.impl;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import io.opentracing.Span;
import io.opentracing.SpanContext;


public class DDSpan implements io.opentracing.Span {

	protected final Tracer tracer;
	protected String operationName;
	protected Map<String, Object> tags;
	protected long startTime;
	protected long startTimeNano; // Only used to measure nano time durations
	protected long durationNano;
	protected final DDSpanContext context;
	protected final LinkedHashSet<Span> traces;

	DDSpan(
			Tracer tracer,
			String operationName,
			LinkedHashSet<Span> traces,
			Map<String, Object> tags,
			Long timestamp,
			DDSpanContext context) {

		this.tracer = tracer;
		this.operationName = operationName;
		this.traces = Optional.ofNullable(traces).orElse(new LinkedHashSet<>());
		this.tags = tags;
		this.startTime = System.currentTimeMillis() * 1000000;
		this.startTimeNano = System.nanoTime();
		this.context = context;

		// track each span of the trace
		this.traces.add(this);
	}

	public SpanContext context() {
		return this.context;
	}

	public void finish() {
		this.durationNano = System.nanoTime() - startTimeNano;
		afterFinish();
	}

	public void finish(long stopTimeMicro) {
		this.durationNano = stopTimeMicro * 1000L - startTime;
		afterFinish();
	}

	protected void afterFinish(){
		if (this.isRootSpan()) {
			this.traces.stream()
			.filter(s -> ((DDSpanContext) s.context()).getSpanId() != ((DDSpanContext) this.context()).getSpanId())
			.forEach(s -> s.finish());
		}
	}

	public void close() {
		this.finish();
	}

	private boolean isRootSpan() {
		return context.getTraceId() == context.getSpanId();
	}

	public io.opentracing.Span setTag(String tag, String value) {
		return this.setTag(tag, value);
	}

	public Span setTag(String tag, boolean value) {
		return this.setTag(tag, value);
	}

	public Span setTag(String tag, Number value) {
		return this.setTag(tag, (Object) value);
	}

	private Span setTag(String tag, Object value) {
		this.tags.put(tag, value);
		return this;
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

	public Span setBaggageItem(String key, String value) {
		this.context.setBaggageItem(key, value);
		return this;
	}

	public String getBaggageItem(String key) {
		return this.context.getBaggageItem(key);
	}

	public Span setOperationName(String operationName) {
		// FIXME: @renaud, the operationName (mandatory) is always set by the constructor
		// FIXME: should be an UnsupportedOperation if we don't want to update the operationName + final
		if (this.operationName != null) {
			throw new IllegalArgumentException("The operationName is already assigned.");
		}
		this.operationName = operationName;
		return this;
	}

	public Span log(String s, Object o) {
		return null;
	}

	public Span log(long l, String s, Object o) {
		return null;
	}

	//Getters and JSON serialisation instructions

	@JsonGetter(value = "name")
	public String getOperationName() {
		return operationName;
	}

	@JsonGetter(value = "meta")
	public Map<String, Object> getTags() {
		return this.tags;
	}

	@JsonGetter(value = "start")
	public long getStartTime() {
		return startTime;
	}

	@JsonGetter(value = "duration")
	public long getDurationNano() {
		return durationNano;
	}

	public String getService() {
		return context.getServiceName();
	}

	@JsonGetter(value = "trace_id")
	public long getTraceId() {
		return context.getTraceId();
	}

	@JsonGetter(value = "span_id")
	public long getSpanId() {
		return context.getSpanId();
	}

	@JsonGetter(value = "parent_id")
	public long getParentId() {
		return context.getParentId();
	}

	@JsonGetter(value = "resource")
	public String getResourceName() {
		return context.getResourceName() == null ? getOperationName() : context.getResourceName();
	}

	public String getType() {
		return context.getSpanType();
	}

	public int getError() {
		return context.getErrorFlag() ? 1 : 0;
	}

	@JsonIgnore
	public LinkedHashSet<Span> getTraces() {
		return traces;
	}
}
