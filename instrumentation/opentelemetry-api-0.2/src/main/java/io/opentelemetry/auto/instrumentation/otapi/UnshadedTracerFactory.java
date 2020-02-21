package io.opentelemetry.auto.instrumentation.otapi;

import unshaded.io.opentelemetry.trace.Tracer;
import unshaded.io.opentelemetry.trace.TracerFactory;

public class UnshadedTracerFactory implements TracerFactory {

  @Override
  public Tracer get(final String instrumentationName) {
    return new UnshadedTracer(
        io.opentelemetry.OpenTelemetry.getTracerFactory().get(instrumentationName));
  }

  @Override
  public Tracer get(final String instrumentationName, final String instrumentationVersion) {
    return new UnshadedTracer(
        io.opentelemetry.OpenTelemetry.getTracerFactory()
            .get(instrumentationName, instrumentationVersion));
  }
}
