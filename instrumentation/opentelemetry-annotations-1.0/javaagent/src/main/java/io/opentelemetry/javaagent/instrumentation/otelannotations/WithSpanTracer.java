/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.otelannotations;

import application.io.opentelemetry.api.trace.Span;
import application.io.opentelemetry.extension.annotations.WithSpan;
import io.opentelemetry.api.trace.Span.Kind;
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

  // we can't conditionally start a span in startSpan() below, because the caller won't know
  // whether to call end() or not on the Span in the returned Context
  public boolean shouldStartSpan(Context context, io.opentelemetry.api.trace.Span.Kind kind) {
    // don't create a nested span if you're not supposed to.
    return shouldStartSpan(kind, context);
  }

  public io.opentelemetry.context.Context startSpan(
      Context context,
      WithSpan applicationAnnotation,
      Method method,
      io.opentelemetry.api.trace.Span.Kind kind) {
    io.opentelemetry.api.trace.Span span =
        startSpan(spanNameForMethodWithAnnotation(applicationAnnotation, method), kind);
    if (kind == io.opentelemetry.api.trace.Span.Kind.SERVER) {
      return withServerSpan(context, span);
    }
    if (kind == io.opentelemetry.api.trace.Span.Kind.CLIENT) {
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
    return "io.opentelemetry.javaagent.opentelemetry-annotations";
  }
}
