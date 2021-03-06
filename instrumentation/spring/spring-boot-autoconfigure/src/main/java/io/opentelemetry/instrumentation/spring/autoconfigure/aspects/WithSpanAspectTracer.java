/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.aspects;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.extension.annotations.WithSpan;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import java.lang.reflect.Method;

class WithSpanAspectTracer extends BaseTracer {
  WithSpanAspectTracer(OpenTelemetry openTelemetry) {
    super(openTelemetry);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.spring-boot-autoconfigure-aspect";
  }

  Context startSpan(Context parentContext, WithSpan annotation, Method method) {
    Span span =
        spanBuilder(parentContext, spanName(annotation, method), annotation.kind()).startSpan();
    switch (annotation.kind()) {
      case SERVER:
        return withServerSpan(parentContext, span);
      case CLIENT:
        return withClientSpan(parentContext, span);
      default:
        return parentContext.with(span);
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
