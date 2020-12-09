/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.otelannotations;

import application.io.opentelemetry.api.trace.Span;
import application.io.opentelemetry.extension.annotations.WithSpan;
import io.opentelemetry.api.trace.Span.Kind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.BaseInstrumenter;
import io.opentelemetry.instrumentation.api.tracer.Tracer;
import java.lang.reflect.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WithSpanTracer extends BaseInstrumenter {
  private static final WithSpanTracer TRACER = new WithSpanTracer();

  public static WithSpanTracer tracer() {
    return TRACER;
  }

  private static final Logger log = LoggerFactory.getLogger(WithSpanTracer.class);

  // we can't conditionally start a span in startSpan() below, because the caller won't know
  // whether to call end() or not on the Span in the returned Context
  public boolean shouldStartSpan(Context context, io.opentelemetry.api.trace.Span.Kind kind) {
    if (kind == io.opentelemetry.api.trace.Span.Kind.SERVER
        && Tracer.getCurrentServerSpan(context) != null) {
      // don't create a nested SERVER span
      return false;
    }
    if (kind == io.opentelemetry.api.trace.Span.Kind.CLIENT
        && context.get(Tracer.CONTEXT_CLIENT_SPAN_KEY) != null) {
      // don't create a nested CLIENT span
      return false;
    }
    return true;
  }

  public io.opentelemetry.context.Context startSpan(
      Context context,
      WithSpan applicationAnnotation,
      Method method,
      io.opentelemetry.api.trace.Span.Kind kind) {
    io.opentelemetry.api.trace.Span span =
        io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.spanFromContext(
            startOperation(spanNameForMethodWithAnnotation(applicationAnnotation, method), kind));
    if (kind == io.opentelemetry.api.trace.Span.Kind.SERVER) {
      context = context.with(Tracer.CONTEXT_SERVER_SPAN_KEY, span);
    }
    if (kind == io.opentelemetry.api.trace.Span.Kind.CLIENT) {
      context = context.with(Tracer.CONTEXT_CLIENT_SPAN_KEY, span);
    }
    return context.with(span);
  }

  /**
   * This method is used to generate an acceptable span (operation) name based on a given method
   * reference. It first checks for existence of {@link WithSpan} annotation. If it is present, then
   * tries to derive name from its {@code value} attribute. Otherwise delegates to {@link
   * Tracer#spanNameForMethod(Method)}.
   */
  public String spanNameForMethodWithAnnotation(WithSpan applicationAnnotation, Method method) {
    if (applicationAnnotation != null && !applicationAnnotation.value().isEmpty()) {
      return applicationAnnotation.value();
    }
    return Tracer.spanNameForMethod(method);
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
