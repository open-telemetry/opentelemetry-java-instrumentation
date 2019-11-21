package datadog.trace.common.sampling;

import datadog.opentracing.DDSpan;
import datadog.opentracing.DDTracer;
import java.math.BigDecimal;
import java.math.BigInteger;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KnuthSampler implements RateSampler {
  private static final BigInteger KNUTH_FACTOR = new BigInteger("1111111111111111111");
  private static final BigDecimal TRACE_ID_MAX_AS_BIG_DECIMAL =
      new BigDecimal(DDTracer.TRACE_ID_MAX);
  private static final BigInteger MODULUS = new BigInteger("2").pow(64);

  private final BigInteger cutoff;
  private final double rate;

  public KnuthSampler(final double rate) {
    this.rate = rate;
    cutoff = new BigDecimal(rate).multiply(TRACE_ID_MAX_AS_BIG_DECIMAL).toBigInteger();

    log.debug("Initializing the RateSampler, sampleRate: {} %", rate * 100);
  }

  @Override
  public boolean sample(final DDSpan span) {
    final boolean sampled;
    if (rate == 1) {
      sampled = true;
    } else if (rate == 0) {
      sampled = false;
    } else {
      sampled = span.getTraceId().multiply(KNUTH_FACTOR).mod(MODULUS).compareTo(cutoff) < 0;
    }

    log.debug("{} - Span is sampled: {}", span, sampled);

    return sampled;
  }

  @Override
  public double getSampleRate() {
    return rate;
  }
}
