/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.aspects;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.extension.annotations.WithSpan;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import java.lang.reflect.Method;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;

public enum WithSpanAspectSpanKindExtractor implements SpanKindExtractor<JoinPoint> {
  INSTANCE;

  @Override
  public SpanKind extract(JoinPoint joinPoint) {
    MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
    Method method = methodSignature.getMethod();
    WithSpan annotation = method.getDeclaredAnnotation(WithSpan.class);
    return annotation.kind();
  }
}
