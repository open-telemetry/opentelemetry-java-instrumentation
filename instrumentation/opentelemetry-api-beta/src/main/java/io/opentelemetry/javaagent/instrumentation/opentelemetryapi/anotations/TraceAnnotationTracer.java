/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.anotations;

import application.io.opentelemetry.api.trace.Span;
import application.io.opentelemetry.extension.annotations.WithSpan;
import io.opentelemetry.api.trace.Span.Kind;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import java.lang.reflect.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TraceAnnotationTracer extends BaseTracer {
  private static final TraceAnnotationTracer TRACER = new TraceAnnotationTracer();

  public static TraceAnnotationTracer tracer() {
    return TRACER;
  }

  private static final Logger log = LoggerFactory.getLogger(TraceAnnotationTracer.class);

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

  public Kind extractSpanKind(WithSpan applicationAnnotation) {
    Span.Kind applicationKind =
        applicationAnnotation != null ? applicationAnnotation.kind() : Span.Kind.INTERNAL;
    return toAgentOrNull(applicationKind);
  }

  public static Kind toAgentOrNull(Span.Kind applicationSpanKind) {
    try {
      return Kind.valueOf(applicationSpanKind.name());
    } catch (IllegalArgumentException e) {
      log.debug("unexpected span kind: {}", applicationSpanKind.name());
      return Kind.INTERNAL;
    }
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.opentelemetry-api-beta";
  }
}
