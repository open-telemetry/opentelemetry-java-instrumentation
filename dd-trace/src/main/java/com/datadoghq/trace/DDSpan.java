package com.datadoghq.trace;

import io.opentracing.Span;

/**
 * Represents an in-flight span in the opentracing system.
 * <p>
 * <p>Spans are created by the {@link DDTracer#buildSpan}.
 * This implementation adds some features according to the DD agent.
 */
public class DDSpan extends DDBaseSpan<Span> implements Span {

    /**
     * A simple constructor.
     * Currently, users have
     *
     * @param timestampMicro if set, use this time instead of the auto-generated time
     * @param context        the context
     */
    protected DDSpan(
            long timestampMicro,
            DDSpanContext context) {
    	super(timestampMicro,context);
    }


	@Override
	protected DDSpan thisInstance() {
		return this;
	} 
}
