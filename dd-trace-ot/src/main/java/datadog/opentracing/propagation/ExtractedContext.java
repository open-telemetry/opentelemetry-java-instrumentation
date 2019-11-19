package datadog.opentracing.propagation;

import java.math.BigInteger;
import java.util.Map;

/**
 * Propagated data resulting from calling tracer.extract with header data from an incoming request.
 */
public class ExtractedContext extends TagContext {
  private final BigInteger traceId;
  private final BigInteger spanId;
  private final Map<String, String> baggage;

  public ExtractedContext(
      final BigInteger traceId, final BigInteger spanId, final Map<String, String> baggage) {
    this.traceId = traceId;
    this.spanId = spanId;
    this.baggage = baggage;
  }

  @Override
  public Iterable<Map.Entry<String, String>> baggageItems() {
    return baggage.entrySet();
  }

  public BigInteger getTraceId() {
    return traceId;
  }

  public BigInteger getSpanId() {
    return spanId;
  }

  public Map<String, String> getBaggage() {
    return baggage;
  }
}
