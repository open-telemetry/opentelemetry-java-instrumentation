package datadog.trace.common.sampling;

import datadog.opentracing.DDSpan;

public interface PrioritySampler {
  void setSamplingPriority(DDSpan span);
}
