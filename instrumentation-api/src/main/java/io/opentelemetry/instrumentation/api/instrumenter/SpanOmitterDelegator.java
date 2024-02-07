/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import java.util.Collections;
import java.util.List;

class SpanOmitterDelegator implements SpanSuppressor {
  private final List<SpanOmitter> spanOmitters;

  SpanOmitterDelegator(List<SpanOmitter> spanOmitters) {
    this.spanOmitters = spanOmitters;
  }

  static SpanSuppressor create(List<SpanOmitter> spanOmitters) {
    return new SpanOmitterDelegator(Collections.unmodifiableList(spanOmitters));
  }

  @CanIgnoreReturnValue
  @Override
  public Context storeInContext(Context context, SpanKind spanKind, Span span) {
    return context;
  }

  @Override
  public boolean shouldSuppress(Context parentContext, SpanKind spanKind) {
    for (SpanOmitter spanOmitter : spanOmitters) {
      if (spanOmitter.shouldOmit(parentContext)) {
        return true;
      }
    }
    return false;
  }
}
