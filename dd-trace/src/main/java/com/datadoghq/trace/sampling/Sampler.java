package com.datadoghq.trace.sampling;

import com.datadoghq.trace.DDBaseSpan;
import com.datadoghq.trace.DDTraceConfig;
import com.datadoghq.trace.DDTracer;
import java.util.Properties;

/** Main interface to sample a collection of traces. */
public interface Sampler {
  static final String ALL_SAMPLER_TYPE = AllSampler.class.getSimpleName();
  static final String RATE_SAMPLER_TYPE = RateSampler.class.getSimpleName();

  /**
   * Sample a collection of traces based on the parent span
   *
   * @param span the parent span with its context
   * @return true when the trace/spans has to be reported/written
   */
  boolean sample(DDBaseSpan<?> span);

  final class Builder {
    public static Sampler forConfig(final Properties config) {
      final Sampler sampler;

      if (config != null) {
        final String configuredType = config.getProperty(DDTraceConfig.SAMPLER_TYPE);
        if (RATE_SAMPLER_TYPE.equals(configuredType)) {
          sampler = new RateSampler(config.getProperty(DDTraceConfig.SAMPLER_RATE));
        } else if (ALL_SAMPLER_TYPE.equals(configuredType)) {
          sampler = new AllSampler();
        } else {
          sampler = DDTracer.UNASSIGNED_SAMPLER;
        }
      } else {
        sampler = DDTracer.UNASSIGNED_SAMPLER;
      }
      return sampler;
    }

    private Builder() {}
  }
}
