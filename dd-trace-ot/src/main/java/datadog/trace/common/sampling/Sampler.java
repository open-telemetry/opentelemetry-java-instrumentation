package datadog.trace.common.sampling;

import datadog.opentracing.DDSpan;
import datadog.trace.api.Config;
import java.util.Map;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;

/** Main interface to sample a collection of traces. */
public interface Sampler {

  /**
   * Sample a collection of traces based on the parent span
   *
   * @param span the parent span with its context
   * @return true when the trace/spans has to be reported/written
   */
  boolean sample(DDSpan span);

  @Slf4j
  final class Builder {
    public static Sampler forConfig(final Config config) {
      Sampler sampler;
      if (config != null) {
        final Map<String, String> serviceRules = config.getTraceSamplingServiceRules();
        final Map<String, String> operationRules = config.getTraceSamplingOperationRules();

        if ((serviceRules != null && !serviceRules.isEmpty())
            || (operationRules != null && !operationRules.isEmpty())
            || config.getTraceSampleRate() != null) {

          try {
            sampler =
                RuleBasedSampler.build(
                    serviceRules,
                    operationRules,
                    config.getTraceSampleRate(),
                    config.getTraceRateLimit());
          } catch (final IllegalArgumentException e) {
            log.error("Invalid sampler configuration. Using AllSampler", e);
            sampler = new AllSampler();
          }
        } else if (config.isPrioritySamplingEnabled()) {
          sampler = new RateByServiceSampler();
        } else {
          sampler = new AllSampler();
        }
      } else {
        sampler = new AllSampler();
      }
      return sampler;
    }

    public static Sampler forConfig(final Properties config) {
      return forConfig(Config.get(config));
    }

    private Builder() {}
  }
}
