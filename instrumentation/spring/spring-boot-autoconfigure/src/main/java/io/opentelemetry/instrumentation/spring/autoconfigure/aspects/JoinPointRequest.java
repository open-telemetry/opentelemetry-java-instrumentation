/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.aspects;

import io.opentelemetry.extension.annotations.WithSpan;
import java.lang.reflect.Method;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;

final class JoinPointRequest {
  private final JoinPoint joinPoint;
  private final Method method;
  private final WithSpan annotation;

  public JoinPointRequest(JoinPoint joinPoint) {
    this.joinPoint = joinPoint;
    MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
    this.method = methodSignature.getMethod();
    this.annotation = this.method.getDeclaredAnnotation(WithSpan.class);
  }

  public Method method() {
    return method;
  }

  public WithSpan annotation() {
    return annotation;
  }

  public Object[] args() {
    return joinPoint.getArgs();
  }
}
