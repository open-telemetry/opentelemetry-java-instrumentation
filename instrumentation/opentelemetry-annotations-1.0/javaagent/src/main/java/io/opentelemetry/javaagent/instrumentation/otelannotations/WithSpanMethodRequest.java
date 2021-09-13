/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.otelannotations;

import application.io.opentelemetry.extension.annotations.WithSpan;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.annotation.support.MethodRequest;
import io.opentelemetry.instrumentation.api.tracer.SpanNames;
import java.lang.reflect.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WithSpanMethodRequest implements MethodRequest {
  private static final Logger logger = LoggerFactory.getLogger(WithSpanMethodRequest.class);

  private final Method method;
  private final WithSpan annotation;
  private final Object[] args;

  public WithSpanMethodRequest(Method method, Object[] args) {
    this.method = method;
    this.annotation = method.getDeclaredAnnotation(WithSpan.class);
    this.args = args;
  }

  @Override
  public Method method() {
    return this.method;
  }

  @Override
  public String name() {
    String spanName = annotation.value();
    if (spanName.isEmpty()) {
      spanName = SpanNames.fromMethod(method);
    }
    return spanName;
  }

  @Override
  public SpanKind kind() {
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
      logger.debug("unexpected span kind: {}", applicationSpanKind.name());
      return SpanKind.INTERNAL;
    }
  }

  @Override
  public Object[] args() {
    return this.args;
  }
}
