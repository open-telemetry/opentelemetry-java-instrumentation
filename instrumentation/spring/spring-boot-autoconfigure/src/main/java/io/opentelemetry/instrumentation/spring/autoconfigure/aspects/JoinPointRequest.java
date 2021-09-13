/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.aspects;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.extension.annotations.WithSpan;
import io.opentelemetry.instrumentation.api.annotation.support.MethodRequest;
import io.opentelemetry.instrumentation.api.tracer.SpanNames;
import java.lang.reflect.Method;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.checkerframework.checker.nullness.qual.Nullable;

final class JoinPointRequest implements MethodRequest {
  private final JoinPoint joinPoint;
  private final Method method;
  private final WithSpan annotation;

  JoinPointRequest(JoinPoint joinPoint) {
    this.joinPoint = joinPoint;
    MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
    this.method = methodSignature.getMethod();
    this.annotation = this.method.getDeclaredAnnotation(WithSpan.class);
  }

  @Override
  public Method method() {
    return method;
  }

  @Override
  public String name() {
    String spanName = annotation.value();
    if (spanName.isEmpty()) {
      return SpanNames.fromMethod(method);
    }
    return spanName;
  }

  @Override
  public SpanKind kind() {
    return annotation.kind();
  }

  @Override
  public @Nullable Object[] args() {
    return joinPoint.getArgs();
  }
}
