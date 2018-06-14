package datadog.opentracing;

import datadog.trace.api.CorrelationIdentifier;
import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;

public class OTTraceCorrelation implements CorrelationIdentifier.Provider {
  public static final OTTraceCorrelation INSTANCE = new OTTraceCorrelation();

  private OTTraceCorrelation() {}

  @Override
  public long getTraceId() {
    final Span activeSpan = GlobalTracer.get().activeSpan();
    if (activeSpan instanceof DDSpan) {
      return ((DDSpan) activeSpan).getTraceId();
    }
    return 0;
  }

  @Override
  public long getSpanId() {
    final Span activeSpan = GlobalTracer.get().activeSpan();
    if (activeSpan instanceof DDSpan) {
      return ((DDSpan) activeSpan).getSpanId();
    }
    return 0;
  }
}
