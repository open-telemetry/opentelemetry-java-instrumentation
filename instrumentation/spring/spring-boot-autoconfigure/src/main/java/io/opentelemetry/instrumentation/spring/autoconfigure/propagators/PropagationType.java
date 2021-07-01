package io.opentelemetry.instrumentation.spring.autoconfigure.propagators;

public enum PropagationType {

  B3, W3C, BAGGAGE, JAEGER, OT_TRACER, NOOP
}
