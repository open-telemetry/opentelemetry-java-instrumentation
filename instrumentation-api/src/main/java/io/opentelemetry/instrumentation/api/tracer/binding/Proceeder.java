package io.opentelemetry.instrumentation.api.tracer.binding;

@FunctionalInterface
public interface Proceeder {
  Object proceed() throws Throwable;
}
