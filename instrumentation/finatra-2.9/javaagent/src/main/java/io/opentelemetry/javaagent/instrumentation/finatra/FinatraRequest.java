/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finatra;

import javax.annotation.Nullable;

public final class FinatraRequest {
  private final Class<?> controllerClass;
  private final Class<?> declaringClass;
  private final String methodName;

  private FinatraRequest(Class<?> controllerClass, Class<?> declaringClass, String methodName) {
    this.controllerClass = controllerClass;
    this.declaringClass = declaringClass;
    this.methodName = methodName;
  }

  public static FinatraRequest create(Class<?> controllerClass) {
    return new FinatraRequest(controllerClass, null, null);
  }

  public static FinatraRequest create(
      Class<?> controllerClass, @Nullable Class<?> declaringClass, @Nullable String methodName) {
    return new FinatraRequest(controllerClass, declaringClass, methodName);
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
