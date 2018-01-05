package datadog.trace.api.sampling;

import datadog.opentracing.DDBaseSpan;
import datadog.trace.api.DDTraceConfig;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;

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

  @Slf4j
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
          log.warn(
              "Sampler type not configured correctly: Type {} not recognized. Defaulting to AllSampler.",
              configuredType);
          sampler = new AllSampler();
        }
      } else {
        log.warn(
            "Sampler type not configured correctly: No config provided! Defaulting to AllSampler.");
        sampler = new AllSampler();
      }
      return sampler;
    }

    private Builder() {}
  }
}
