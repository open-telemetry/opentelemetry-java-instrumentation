package io.opentelemetry.javaagent.tooling;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;

public interface OnStartSpanProcessor extends SpanProcessor {
  @Override
  default boolean isStartRequired() {
    return true;
  }

  @Override
  default void onEnd(ReadableSpan span) {}

  @Override
  default boolean isEndRequired() {
    return false;
  }

  @Override
  default CompletableResultCode shutdown() {
    return CompletableResultCode.ofSuccess();
  }

  @Override
  default CompletableResultCode forceFlush() {
    return CompletableResultCode.ofSuccess();
  }
}
