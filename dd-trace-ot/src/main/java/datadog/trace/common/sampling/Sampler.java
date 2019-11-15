package datadog.trace.common.sampling;

import datadog.opentracing.DDSpan;
import datadog.trace.api.Config;
import java.util.Properties;

/** Main interface to sample a collection of traces. */
public interface Sampler {

  /**
   * Sample a collection of traces based on the parent span
   *
   * @param span the parent span with its context
   * @return true when the trace/spans has to be reported/written
   */
  boolean sample(DDSpan span);

  final class Builder {
    public static Sampler forConfig(final Config config) {
      final Sampler sampler;
      if (config != null) {
        if (config.isPrioritySamplingEnabled()) {
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
