package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanContext;

public abstract class SqsMessageDelegate<T> {
  public abstract int getPayloadSize(T message);
  public abstract SpanContext getUpstreamContext(OpenTelemetry openTelemetry, T message);
}
