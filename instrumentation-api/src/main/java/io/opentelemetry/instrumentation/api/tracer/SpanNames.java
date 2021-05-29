/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import java.lang.reflect.Method;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class SpanNames {
  /**
   * This method is used to generate an acceptable span (operation) name based on a given method
   * reference. Anonymous classes are named based on their parent.
   */
  public static String spanNameForMethod(Method method) {
    return spanNameForMethod(method.getDeclaringClass(), method.getName());
  }

  /**
   * This method is used to generate an acceptable span (operation) name based on a given method
   * reference. Anonymous classes are named based on their parent.
   */
  public static String spanNameForMethod(Class<?> clazz, @Nullable Method method) {
    return spanNameForMethod(clazz, method == null ? "<unknown>" : method.getName());
  }

  /**
   * This method is used to generate an acceptable span (operation) name based on a given method
   * reference. Anonymous classes are named based on their parent.
   */
  public static String spanNameForMethod(Class<?> cl, String methodName) {
    return spanNameForClass(cl) + "." + methodName;
  }

  /**
   * This method is used to generate an acceptable span (operation) name based on a given class
   * reference. Anonymous classes are named based on their parent.
   */
  public static String spanNameForClass(Class<?> clazz) {
    if (!clazz.isAnonymousClass()) {
      return clazz.getSimpleName();
    }
    String className = clazz.getName();
    if (clazz.getPackage() != null) {
      String pkgName = clazz.getPackage().getName();
      if (!pkgName.isEmpty()) {
        className = className.substring(pkgName.length() + 1);
      }
    }
    return className;
  }

  private SpanNames() {}
}
