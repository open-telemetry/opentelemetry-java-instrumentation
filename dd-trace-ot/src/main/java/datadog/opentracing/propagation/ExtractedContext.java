package datadog.opentracing.propagation;

import io.opentracing.SpanContext;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class ExtractedContext implements SpanContext {
  private final Long traceId;
  private final Long spanId;
  private final int samplingPriority;
  private final Map<String, String> baggage;
  private final Map<String, String> tags;
  private final AtomicBoolean samplingPriorityLocked = new AtomicBoolean(false);

  public ExtractedContext(
      final Long traceId,
      final Long spanId,
      final int samplingPriority,
      final Map<String, String> baggage,
      final Map<String, String> tags) {
    this.traceId = traceId;
    this.spanId = spanId;
    this.samplingPriority = samplingPriority;
    this.baggage = baggage;
    this.tags = tags;
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

  public Map<String, String> getTags() {
    return tags;
  }

  public boolean getSamplingPriorityLocked() {
    return samplingPriorityLocked.get();
  }
}
