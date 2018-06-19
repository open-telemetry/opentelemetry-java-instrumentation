package datadog.opentracing;

import com.google.common.annotations.VisibleForTesting;
import datadog.trace.api.CorrelationIdentifier;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;

public class OTTraceCorrelation implements CorrelationIdentifier.Provider {
  public static final OTTraceCorrelation INSTANCE = new OTTraceCorrelation();

  private final Tracer tracer;

  private OTTraceCorrelation() {
    // GlobalTracer.get() is guaranteed to return a constant so we can keep reference to it
    this(GlobalTracer.get());
  }

  @VisibleForTesting
  OTTraceCorrelation(Tracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public long getTraceId() {
    final Span activeSpan = tracer.activeSpan();
    if (activeSpan instanceof DDSpan) {
      return ((DDSpan) activeSpan).getTraceId();
    }
    return 0;
  }

  @Override
  public long getSpanId() {
    final Span activeSpan = tracer.activeSpan();
    if (activeSpan instanceof DDSpan) {
      return ((DDSpan) activeSpan).getSpanId();
    }
    return 0;
  }
}
