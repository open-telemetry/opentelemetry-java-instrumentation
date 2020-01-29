package io.opentelemetry.auto.instrumentation.ratpack;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.trace.Tracer;

public class TracerHolder {
  public static final Tracer TRACER = OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto");
}
