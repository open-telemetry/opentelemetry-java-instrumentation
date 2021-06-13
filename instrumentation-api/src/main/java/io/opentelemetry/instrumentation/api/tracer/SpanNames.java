/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import java.lang.reflect.Method;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class SpanNames {
  /**
   * This method is used to generate an acceptable span name based on a given method reference.
   * Anonymous classes are named based on their parent.
   */
  public static String from(Method method) {
    return from(method.getDeclaringClass(), method.getName());
  }

  /**
   * This method is used to generate an acceptable span name based on a given method reference.
   * Anonymous classes are named based on their parent.
   */
  public static String from(Class<?> clazz, @Nullable Method method) {
    return from(clazz, method == null ? "<unknown>" : method.getName());
  }

  /**
   * This method is used to generate an acceptable span name based on a given method reference.
   * Anonymous classes are named based on their parent.
   */
  public static String from(Class<?> cl, String methodName) {
    return ClassNames.simpleName(cl) + "." + methodName;
  }

  private SpanNames() {}
}
