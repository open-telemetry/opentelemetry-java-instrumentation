// This file includes software developed at SignalFx

package io.opentelemetry.auto.instrumentation.springdata;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.decorator.ClientDecorator;
import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.lang.reflect.Method;

public final class SpringDataDecorator extends ClientDecorator {
  public static final SpringDataDecorator DECORATOR = new SpringDataDecorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto.spring-data-1.8");

  private SpringDataDecorator() {}

  @Override
  protected String service() {
    return null;
  }

  @Override
  protected String getSpanType() {
    return null;
  }

  @Override
  protected String getComponentName() {
    return "spring-data";
  }

  public Span onOperation(final Span span, final Method method) {
    assert span != null;
    assert method != null;

    if (method != null) {
      span.setAttribute(MoreTags.RESOURCE_NAME, spanNameForMethod(method));
    }
    return span;
  }
}
