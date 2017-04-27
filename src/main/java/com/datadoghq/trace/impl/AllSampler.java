package com.datadoghq.trace.impl;

import com.datadoghq.trace.Sampler;

/**
 * Sampler that always says yes...
 */
public class AllSampler implements Sampler {

	@Override
	public boolean sample(DDSpan span) {
		return true;
	}

}
