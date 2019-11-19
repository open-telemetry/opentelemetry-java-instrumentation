package datadog.opentracing.propagation;

import io.opentracing.SpanContext;
import java.math.BigInteger;
import java.util.Collections;
import java.util.Map;

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

  @Override
  public String toTraceId() {
    return "";
  }

  @Override
  public String toSpanId() {
    return "";
  }

  @Override
  public Iterable<Map.Entry<String, String>> baggageItems() {
    return Collections.emptyList();
  }

  public BigInteger getTraceId() {
    return traceId;
  }

  public BigInteger getSpanId() {
    return spanId;
  }
}
