/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.ContextSpanProcessorUtil;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import java.util.function.BiConsumer;

/** A span processor that retrieves the actual processing function from context. */
final class ContextSpanProcessor implements SpanProcessor {

  @Override
  public void onStart(Context context, ReadWriteSpan readWriteSpan) {
    BiConsumer<Context, Span> spanProcessor = ContextSpanProcessorUtil.fromContextOrNull(context);
    if (spanProcessor != null) {
      spanProcessor.accept(context, readWriteSpan);
    }
  }

  @Override
  public boolean isStartRequired() {
    return true;
  }

  @Override
  public void onEnd(ReadableSpan readableSpan) {}

  @Override
  public boolean isEndRequired() {
    return false;
  }
}
