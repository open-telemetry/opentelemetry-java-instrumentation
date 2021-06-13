/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import java.lang.reflect.Method;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class SpanNames {
  /**
   * This method is used to generate a span name based on a method. Anonymous classes are named
   * based on their parent.
   */
  public static String fromMethod(Method method) {
    return fromMethod(method.getDeclaringClass(), method.getName());
  }

  /**
   * This method is used to generate a span name based on a method. Anonymous classes are named
   * based on their parent.
   */
  public static String fromMethod(Class<?> clazz, @Nullable Method method) {
    return fromMethod(clazz, method == null ? "<unknown>" : method.getName());
  }

  /**
   * This method is used to generate a span name based on a method. Anonymous classes are named
   * based on their parent.
   */
  public static String fromMethod(Class<?> cl, String methodName) {
    return ClassNames.simpleName(cl) + "." + methodName;
  }

  private SpanNames() {}
}
