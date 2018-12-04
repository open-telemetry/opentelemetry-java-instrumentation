package datadog.trace.tracer.sampling;

import datadog.trace.tracer.Trace;

/**
 * Keeps or discards traces.
 *
 * <p>Note that in most cases the sampler will keep all traces. Most of the sampling logic is done
 * downstream by the trace-agent or dd-backend.
 */
public interface Sampler {
  /**
   * Run tracer sampling logic on the trace.
   *
   * @param trace
   * @return true if the trace should be kept/written/reported.
   */
  boolean sample(Trace trace);
}
