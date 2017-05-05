package com.datadoghq.trace.sampling;

import io.opentracing.Span;

/**
 * Sampler that always says yes...
 */
public class AllSampler implements Sampler {

	@Override
	public boolean sample(Span span) {
		return true;
	}

}
