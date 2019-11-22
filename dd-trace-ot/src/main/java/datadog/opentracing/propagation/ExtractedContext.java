package datadog.opentracing.propagation;

import datadog.opentracing.SpanContext;
import java.math.BigInteger;

/**
 * Propagated data resulting from calling tracer.extract with header data from an incoming request.
 */
public class ExtractedContext implements SpanContext {
  private final BigInteger traceId;
  private final BigInteger spanId;

  public ExtractedContext(final BigInteger traceId, final BigInteger spanId) {
    this.traceId = traceId;
    this.spanId = spanId;
  }

  public BigInteger getTraceId() {
    return traceId;
  }

  public BigInteger getSpanId() {
    return spanId;
  }
}
