/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import java.util.Set;

final class SuppressIfSameSpanKeyStrategy extends SpanSuppressionStrategy {

  private final Set<SpanKey> outgoingSpanKeys;

  SuppressIfSameSpanKeyStrategy(Set<SpanKey> outgoingSpanKeys) {
    this.outgoingSpanKeys = outgoingSpanKeys;
  }

  @Override
  Context storeInContext(Context context, SpanKind spanKind, Span span) {
    for (SpanKey outgoingSpanKey : outgoingSpanKeys) {
      context = outgoingSpanKey.storeInContext(context, span);
    }
    return context;
  }

  @Override
  boolean shouldSuppress(Context parentContext, SpanKind spanKind) {
    for (SpanKey outgoingSpanKey : outgoingSpanKeys) {
      if (outgoingSpanKey.fromContextOrNull(parentContext) == null) {
        return false;
      }
    }
    return true;
  }
}
