package io.opentelemetry.auto.instrumentation.trace_annotation;

import io.opentelemetry.auto.decorator.BaseDecorator;

public class TraceDecorator extends BaseDecorator {
  public static TraceDecorator DECORATE = new TraceDecorator();

  @Override
  protected String getSpanType() {
    return null;
  }

  @Override
  protected String getComponentName() {
    return "trace";
  }
}
