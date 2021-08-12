/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import java.util.List;

final class NeverSuppressAndStoreStrategy extends SpanSuppressionStrategy {

  private final List<SpanKey> outgoingSpanKeys;

  NeverSuppressAndStoreStrategy(List<SpanKey> outgoingSpanKeys) {
    this.outgoingSpanKeys = outgoingSpanKeys;
  }

  @Override
  Context storeInContext(Context context, SpanKind spanKind, Span span) {
    for (SpanKey outgoingSpanKey : outgoingSpanKeys) {
      context = outgoingSpanKey.with(context, span);
    }
    return context;
  }

  @Override
  boolean shouldSuppress(Context parentContext, SpanKind spanKind) {
    return false;
  }
}
