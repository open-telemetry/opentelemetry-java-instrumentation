package datadog.trace.tracer;

import datadog.trace.api.sampling.PrioritySampling;
import java.util.concurrent.ThreadLocalRandom;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode
class SpanContextImpl implements SpanContext {

  public static final String ZERO = "0";

  private final String traceId;
  private final String parentId;
  private final String spanId;

  SpanContextImpl(final String traceId, final String parentId, final String spanId) {
    this.traceId = traceId;
    this.parentId = parentId;
    this.spanId = spanId;
  }

  @Override
  public String getTraceId() {
    return traceId;
  }

  @Override
  public String getParentId() {
    return parentId;
  }

  @Override
  public String getSpanId() {
    return spanId;
  }

  // TODO: Implement proper priority handling methods
  @Override
  public Integer getSamplingFlags() {
    return PrioritySampling.SAMPLER_KEEP;
  }

  static SpanContext fromParent(final SpanContext parent) {
    final String traceId;
    final String parentId;
    if (parent == null) {
      traceId = generateNewId();
      parentId = ZERO;
    } else {
      traceId = parent.getTraceId();
      parentId = parent.getSpanId();
    }
    return new SpanContextImpl(traceId, parentId, generateNewId());
  }

  static String generateNewId() {
    // TODO: expand the range of numbers generated to be from 1 to uint 64 MAX
    // Ensure the generated ID is in a valid range:
    return String.valueOf(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE));
  }
}
