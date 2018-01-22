package datadog.trace.common.sampling;

import datadog.opentracing.DDSpan;
import datadog.trace.common.DDTraceConfig;
import java.util.Properties;

/** Main interface to sample a collection of traces. */
public interface Sampler {
  static final String ALL_SAMPLER_TYPE = AllSampler.class.getSimpleName();

  /**
   * Sample a collection of traces based on the parent span
   *
   * @param span the parent span with its context
   * @return true when the trace/spans has to be reported/written
   */
  boolean sample(DDSpan span);

  final class Builder {
    public static Sampler forConfig(final Properties config) {
      final Sampler sampler;
      if (config != null) {
        final boolean prioritySamplingEnabled =
            Boolean.parseBoolean(config.getProperty(DDTraceConfig.PRIORITY_SAMPLING));
        if (prioritySamplingEnabled) {
          sampler = new RateByServiceSampler();
        } else {
          sampler = new AllSampler();
        }
      } else {
        sampler = new AllSampler();
      }
      return sampler;
    }

    private Builder() {}
  }
}
