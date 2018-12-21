package datadog.trace.tracer.sampling;

import datadog.trace.tracer.Trace;

/** Sampler that samples all traces. */
public class AllSampler implements Sampler {
  @Override
  public boolean sample(final Trace trace) {
    return true;
  }
}
