/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finatra.v2_9;

import javax.annotation.Nullable;

public class FinatraRequest {
  private final Class<?> controllerClass;
  @Nullable private final Class<?> declaringClass;
  @Nullable private final String methodName;

  public static FinatraRequest create(Class<?> controllerClass) {
    return new FinatraRequest(controllerClass, null, null);
  }

  public static FinatraRequest create(
      Class<?> controllerClass, @Nullable Class<?> declaringClass, @Nullable String methodName) {
    return new FinatraRequest(controllerClass, declaringClass, methodName);
  }

  private FinatraRequest(
      Class<?> controllerClass, @Nullable Class<?> declaringClass, @Nullable String methodName) {
    this.controllerClass = controllerClass;
    this.declaringClass = declaringClass;
    this.methodName = methodName;
  }

  public Class<?> controllerClass() {
    return controllerClass;
  }

  @Nullable
  public Class<?> declaringClass() {
    return declaringClass;
  }

  @Nullable
  public String methodName() {
    return methodName;
  }
}
