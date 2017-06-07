package com.datadoghq.trace;

import io.opentracing.ActiveSpan;

/**
 * Base implementation for opentracing {@link ActiveSpan}
 */
public class DDActiveSpan extends DDBaseSpan<ActiveSpan> implements ActiveSpan{

	protected final DDActiveSpan parent;
	
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
	public DDActiveSpan getParent() {
		return parent;
	}

	@Override
	public void deactivate() {
		context().getTracer().deactivate(this);
		finish();
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
}
