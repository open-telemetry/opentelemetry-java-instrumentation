package com.datadoghq.trace.sampling;

import io.opentracing.Span;

/**
 * Sampler that always says yes...
 */
public class AllSampler extends ASampler {

	@Override
	public boolean doSample(Span span) {
		return true;
	}

}
