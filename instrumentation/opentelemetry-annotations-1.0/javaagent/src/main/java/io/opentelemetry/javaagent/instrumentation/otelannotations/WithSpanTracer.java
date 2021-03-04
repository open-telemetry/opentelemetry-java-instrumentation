/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.otelannotations;

import application.io.opentelemetry.extension.annotations.WithSpan;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import java.lang.reflect.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WithSpanTracer extends BaseTracer {
  private static final WithSpanTracer TRACER = new WithSpanTracer();

  public static WithSpanTracer tracer() {
    return TRACER;
  }

  private static final Logger log = LoggerFactory.getLogger(WithSpanTracer.class);

  public Context startSpan(
      Context context, WithSpan applicationAnnotation, Method method, SpanKind kind) {
    Span span =
        spanBuilder(spanNameForMethodWithAnnotation(applicationAnnotation, method), kind)
            .setParent(context)
            .startSpan();
    if (kind == SpanKind.SERVER) {
      return withServerSpan(context, span);
    }
    if (kind == SpanKind.CLIENT) {
      return withClientSpan(context, span);
    }
    return context.with(span);
  }

  /**
   * This method is used to generate an acceptable span (operation) name based on a given method
   * reference. It first checks for existence of {@link WithSpan} annotation. If it is present, then
   * tries to derive name from its {@code value} attribute. Otherwise delegates to {@link
   * #spanNameForMethod(Method)}.
   */
  public String spanNameForMethodWithAnnotation(WithSpan applicationAnnotation, Method method) {
    if (applicationAnnotation != null && !applicationAnnotation.value().isEmpty()) {
      return applicationAnnotation.value();
    }
    return spanNameForMethod(method);
  }

  public SpanKind extractSpanKind(WithSpan applicationAnnotation) {
    application.io.opentelemetry.api.trace.SpanKind applicationKind =
        applicationAnnotation != null
            ? applicationAnnotation.kind()
            : application.io.opentelemetry.api.trace.SpanKind.INTERNAL;
    return toAgentOrNull(applicationKind);
  }

  public static SpanKind toAgentOrNull(
      application.io.opentelemetry.api.trace.SpanKind applicationSpanKind) {
    try {
      return SpanKind.valueOf(applicationSpanKind.name());
    } catch (IllegalArgumentException e) {
      log.debug("unexpected span kind: {}", applicationSpanKind.name());
      return SpanKind.INTERNAL;
    }
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.opentelemetry-annotations-1.0";
  }
}
