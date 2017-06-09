package com.datadoghq.trace;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.opentracing.ActiveSpan;

/**
 * Base implementation for opentracing {@link ActiveSpan}
 */
public class DDActiveSpan extends DDBaseSpan<ActiveSpan> implements ActiveSpan{

	protected final DDActiveSpan parent;
	protected boolean deactivated = false;
	
	protected DDActiveSpan(DDActiveSpan parent,DDSpan span) {
		super(span.startTimeMicro, span.context());
		this.startTimeNano = span.startTimeNano;
		this.durationNano = span.durationNano;
		this.parent = parent;
	}
	
	protected DDActiveSpan(DDActiveSpan parent,long timestampMicro, DDSpanContext context) {
		super(timestampMicro, context);
		this.parent = parent;
	}
	
	/**
	 * @return the generating parent if not null
	 */
	@JsonIgnore
	public DDActiveSpan getParent() {
		return parent;
	}
	
	/**
	 * @return true if the span has already been deactivated
	 */
	@JsonIgnore
	public boolean isDeactivated() {
		return deactivated;
	}

	@Override
	public void deactivate() {
		DDTracer tracer = context().getTracer();
		if(tracer!=null){
			tracer.deactivate(this);
		}
		finish();
		deactivated = true;
	}

	@Override
	public Continuation capture() {
		return new DDContinuation();
	}

	@Override
	protected ActiveSpan thisInstance() {
		return this;
	}
	
	public class DDContinuation implements Continuation{
		
		@Override
		public ActiveSpan activate() {
			//Reactivate the current span
			context().getTracer().makeActive(DDActiveSpan.this);
			
			//And return the encapsulating ActiveSpan
			return DDActiveSpan.this;
		}
		
	}

	@Override
	public void close() {
		deactivate();
	}
}
