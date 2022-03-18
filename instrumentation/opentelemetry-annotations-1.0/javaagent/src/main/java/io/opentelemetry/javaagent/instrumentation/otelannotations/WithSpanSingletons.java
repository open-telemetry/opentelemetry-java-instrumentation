/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.otelannotations;

import application.io.opentelemetry.extension.annotations.WithSpan;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.annotation.support.MethodSpanAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNames;
import java.lang.reflect.Method;
import java.util.logging.Logger;

public final class WithSpanSingletons {
  private static final String INSTRUMENTATION_NAME =
      "io.opentelemetry.opentelemetry-annotations-1.0";

  private static final Logger logger = Logger.getLogger(WithSpanSingletons.class.getName());
  private static final Instrumenter<Method, Object> INSTRUMENTER = createInstrumenter();
  private static final Instrumenter<MethodRequest, Object> INSTRUMENTER_WITH_ATTRIBUTES =
      createInstrumenterWithAttributes();

  public static Instrumenter<Method, Object> instrumenter() {
    return INSTRUMENTER;
  }

  public static Instrumenter<MethodRequest, Object> instrumenterWithAttributes() {
    return INSTRUMENTER_WITH_ATTRIBUTES;
  }

  private static Instrumenter<Method, Object> createInstrumenter() {
    return Instrumenter.builder(
            GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, WithSpanSingletons::spanNameFromMethod)
        .newInstrumenter(WithSpanSingletons::spanKindFromMethod);
  }

  private static Instrumenter<MethodRequest, Object> createInstrumenterWithAttributes() {
    return Instrumenter.builder(
            GlobalOpenTelemetry.get(),
            INSTRUMENTATION_NAME,
            WithSpanSingletons::spanNameFromMethodRequest)
        .addAttributesExtractor(
            MethodSpanAttributesExtractor.newInstance(
                MethodRequest::method,
                WithSpanParameterAttributeNamesExtractor.INSTANCE,
                MethodRequest::args))
        .newInstrumenter(WithSpanSingletons::spanKindFromMethodRequest);
  }

  private static SpanKind spanKindFromMethodRequest(MethodRequest request) {
    return spanKindFromMethod(request.method());
  }

  private static SpanKind spanKindFromMethod(Method method) {
    WithSpan annotation = method.getDeclaredAnnotation(WithSpan.class);
    if (annotation == null) {
      return SpanKind.INTERNAL;
    }
    return toAgentOrNull(annotation.kind());
  }

  private static SpanKind toAgentOrNull(
      application.io.opentelemetry.api.trace.SpanKind applicationSpanKind) {
    try {
      return SpanKind.valueOf(applicationSpanKind.name());
    } catch (IllegalArgumentException e) {
      logger.fine("unexpected span kind: " + applicationSpanKind.name());
      return SpanKind.INTERNAL;
    }
  }

  private static String spanNameFromMethodRequest(MethodRequest request) {
    return spanNameFromMethod(request.method());
  }

  private static String spanNameFromMethod(Method method) {
    WithSpan annotation = method.getDeclaredAnnotation(WithSpan.class);
    String spanName = annotation.value();
    if (spanName.isEmpty()) {
      spanName = SpanNames.fromMethod(method);
    }
    return spanName;
  }
}
