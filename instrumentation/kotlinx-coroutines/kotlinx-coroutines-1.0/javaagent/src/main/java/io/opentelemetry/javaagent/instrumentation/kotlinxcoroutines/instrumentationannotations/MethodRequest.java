/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.instrumentationannotations;

import io.opentelemetry.api.trace.SpanKind;
import javax.annotation.Nullable;

class MethodRequest {
  private final Class<?> declaringClass;
  private final String methodName;
  @Nullable private final String withSpanValue;
  private final SpanKind spanKind;

  static MethodRequest create(
      Class<?> declaringClass,
      String methodName,
      @Nullable String withSpanValue,
      SpanKind spanKind) {
    return new MethodRequest(declaringClass, methodName, withSpanValue, spanKind);
  }

  private MethodRequest(
      Class<?> declaringClass,
      String methodName,
      @Nullable String withSpanValue,
      SpanKind spanKind) {
    this.declaringClass = declaringClass;
    this.methodName = methodName;
    this.withSpanValue = withSpanValue;
    this.spanKind = spanKind;
  }

  Class<?> getDeclaringClass() {
    return declaringClass;
  }

  String getMethodName() {
    return methodName;
  }

  @Nullable
  String getWithSpanValue() {
    return withSpanValue;
  }

  SpanKind getSpanKind() {
    return spanKind;
  }
}
