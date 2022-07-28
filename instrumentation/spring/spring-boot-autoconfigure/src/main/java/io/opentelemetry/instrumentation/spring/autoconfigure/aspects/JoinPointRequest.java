/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.aspects;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.opentelemetry.instrumentation.api.util.SpanNames;
import java.lang.reflect.Method;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;

final class JoinPointRequest {

  private final JoinPoint joinPoint;
  private final Method method;
  private final String spanName;
  private final SpanKind spanKind;

  JoinPointRequest(JoinPoint joinPoint) {
    this.joinPoint = joinPoint;
    MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
    this.method = methodSignature.getMethod();

    // in rare cases, when interface method does not have annotations but the implementation does,
    // and the AspectJ factory is configured to proxy interfaces, this class will receive the
    // abstract interface method (without annotations) instead of the implementation method (with
    // annotations); these defaults prevent NPEs in this scenario
    String spanName = "";
    SpanKind spanKind = SpanKind.INTERNAL;

    io.opentelemetry.extension.annotations.WithSpan oldAnnotation =
        this.method.getDeclaredAnnotation(io.opentelemetry.extension.annotations.WithSpan.class);
    if (oldAnnotation != null) {
      spanName = oldAnnotation.value();
      spanKind = oldAnnotation.kind();
    }

    WithSpan annotation = this.method.getDeclaredAnnotation(WithSpan.class);
    if (annotation != null) {
      spanName = annotation.value();
      spanKind = annotation.kind();
    }

    if (spanName.isEmpty()) {
      spanName = SpanNames.fromMethod(method);
    }

    this.spanName = spanName;
    this.spanKind = spanKind;
  }

  String spanName() {
    return spanName;
  }

  SpanKind spanKind() {
    return spanKind;
  }

  Method method() {
    return method;
  }

  Object[] args() {
    return joinPoint.getArgs();
  }
}
