/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;

final class NoopSuppressionStrategy extends SpanSuppressionStrategy {

  static final SpanSuppressionStrategy INSTANCE = new NoopSuppressionStrategy();

  @Override
  Context storeInContext(Context context, SpanKind spanKind, Span span) {
    return context;
  }

  @Override
  boolean shouldSuppress(Context parentContext, SpanKind spanKind) {
    return false;
  }
}
