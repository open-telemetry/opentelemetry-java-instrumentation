/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.instrumentationannotations;

import io.opentelemetry.api.trace.SpanKind;

public final class MethodRequest {
  private final Class<?> declaringClass;
  private final String methodName;
  private final String withSpanValue;
  private final SpanKind spanKind;

  private MethodRequest(
      Class<?> declaringClass, String methodName, String withSpanValue, SpanKind spanKind) {
    this.declaringClass = declaringClass;
    this.methodName = methodName;
    this.withSpanValue = withSpanValue;
    this.spanKind = spanKind;
  }

  public static MethodRequest create(
      Class<?> declaringClass, String methodName, String withSpanValue, SpanKind spanKind) {
    return new MethodRequest(declaringClass, methodName, withSpanValue, spanKind);
  }

  public Class<?> getDeclaringClass() {
    return declaringClass;
  }

  public String getMethodName() {
    return methodName;
  }

  public String getWithSpanValue() {
    return withSpanValue;
  }

  public SpanKind getSpanKind() {
    return spanKind;
  }
}
