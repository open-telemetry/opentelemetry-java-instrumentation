/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.aspects;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.extension.annotations.WithSpan;
import io.opentelemetry.instrumentation.api.tracer.BaseMethodTracer;
import java.lang.reflect.Method;

class WithSpanAspectTracer extends BaseMethodTracer {
  WithSpanAspectTracer(OpenTelemetry openTelemetry) {
    super(openTelemetry);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.spring-boot-autoconfigure-aspect";
  }

  Context startSpan(Context parentContext, WithSpan annotation, Method method) {
    Context spanStrategyContext = withMethodSpanStrategy(parentContext, method);
    Span span =
        spanBuilder(parentContext, spanName(annotation, method), annotation.kind()).startSpan();

    switch (annotation.kind()) {
      case SERVER:
        return withServerSpan(spanStrategyContext, span);
      case CLIENT:
        return withClientSpan(spanStrategyContext, span);
      default:
        return spanStrategyContext.with(span);
    }
  }

  private String spanName(WithSpan annotation, Method method) {
    String spanName = annotation.value();
    if (spanName.isEmpty()) {
      return spanNameForMethod(method);
    }
    return spanName;
  }
}
