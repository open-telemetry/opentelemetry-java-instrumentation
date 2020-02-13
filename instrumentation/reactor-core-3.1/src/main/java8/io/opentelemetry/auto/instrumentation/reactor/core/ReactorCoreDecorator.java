package io.opentelemetry.auto.instrumentation.reactor.core;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.decorator.BaseDecorator;
import io.opentelemetry.auto.instrumentation.api.SpanTypes;
import io.opentelemetry.trace.Tracer;

public class ReactorCoreDecorator extends BaseDecorator {
  public static ReactorCoreDecorator DECORATE = new ReactorCoreDecorator();
  public static final Tracer TRACER =
      OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto.reactor-core-3.1");

  @Override
  protected String getSpanType() {
    return SpanTypes.HTTP_CLIENT; // TODO: Is this the correct type?
  }

  @Override
  protected String getComponentName() {
    return "reactor-core";
  }
}
