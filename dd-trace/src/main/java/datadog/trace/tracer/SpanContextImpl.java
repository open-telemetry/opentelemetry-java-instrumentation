package datadog.trace.tracer;

import datadog.trace.api.sampling.PrioritySampling;
import java.math.BigInteger;
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

  /** @return Random 64bit unsigned number strictly greater than zero */
  static String generateNewId() {
    // Note: we can probably optimize this using {@link ThreadLocalRandom#nextLong()}
    // and {@link Long#toUnsignedString} but {@link Long#toUnsignedString} is only
    // available in Java8+ and {@link ThreadLocalRandom#nextLong()} cannot
    // generate negative numbers before Java8.
    BigInteger result = BigInteger.ZERO;
    while (result.equals(BigInteger.ZERO)) {
      result = new BigInteger(64, ThreadLocalRandom.current());
    }
    return result.toString();
  }
}
