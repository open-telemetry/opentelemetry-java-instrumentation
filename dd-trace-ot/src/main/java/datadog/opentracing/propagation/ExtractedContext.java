package datadog.opentracing.propagation;

import io.opentracing.SpanContext;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class ExtractedContext implements SpanContext {
  private final Long traceId;
  private final Long spanId;
  private final int samplingPriority;
  private final Map<String, String> baggage;
  private final AtomicBoolean samplingPriorityLocked = new AtomicBoolean(false);

  public ExtractedContext(
      final Long traceId,
      final Long spanId,
      final int samplingPriority,
      final Map<String, String> baggage) {
    this.traceId = traceId;
    this.spanId = spanId;
    this.samplingPriority = samplingPriority;
    this.baggage = baggage;
  }

  @Override
  public Iterable<Map.Entry<String, String>> baggageItems() {
    return baggage.entrySet();
  }

  public void lockSamplingPriority() {
    samplingPriorityLocked.set(true);
  }

  public Long getTraceId() {
    return traceId;
  }

  public Long getSpanId() {
    return spanId;
  }

  public int getSamplingPriority() {
    return samplingPriority;
  }

  public Map<String, String> getBaggage() {
    return baggage;
  }

  public boolean getSamplingPriorityLocked() {
    return samplingPriorityLocked.get();
  }
}
