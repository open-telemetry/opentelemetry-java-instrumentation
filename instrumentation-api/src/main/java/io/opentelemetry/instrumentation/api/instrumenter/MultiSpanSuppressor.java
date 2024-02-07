package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import java.util.Arrays;
import java.util.List;

class MultiSpanSuppressor implements SpanSuppressor {
  private final List<SpanSuppressor> suppressors;

  static SpanSuppressor create(SpanSuppressor... suppressors) {
    return new MultiSpanSuppressor(Arrays.asList(suppressors));
  }

  private MultiSpanSuppressor(List<SpanSuppressor> suppressors) {
    this.suppressors = suppressors;
  }

  @Override
  public Context storeInContext(Context context, SpanKind spanKind, Span span) {
    for (SpanSuppressor suppressor : suppressors) {
      context = suppressor.storeInContext(context, spanKind, span);
    }
    return context;
  }

  @Override
  public boolean shouldSuppress(Context parentContext, SpanKind spanKind) {
    for (SpanSuppressor suppressor : suppressors) {
      if(suppressor.shouldSuppress(parentContext, spanKind)) {
        return true;
      }
    }
    return false;
  }
}
