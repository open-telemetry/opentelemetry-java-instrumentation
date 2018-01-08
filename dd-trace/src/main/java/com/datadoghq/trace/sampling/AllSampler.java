package com.datadoghq.trace.sampling;

import com.datadoghq.trace.DDBaseSpan;

/** Sampler that always says yes... */
public class AllSampler extends AbstractSampler {

  @Override
  public boolean doSample(final DDBaseSpan<?> span) {
    return true;
  }

  @Override
  public String toString() {
    return "AllSampler { sample=true }";
  }
}
