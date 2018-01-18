package datadog.trace.common.sampling;

import datadog.opentracing.DDSpan;
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
      return new AllSampler();
    }

    private Builder() {}
  }
}
