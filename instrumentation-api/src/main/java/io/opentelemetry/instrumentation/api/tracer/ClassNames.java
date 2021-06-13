/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

public class ClassNames {

  /**
   * This method is used to generate a simple name based on a given class reference, e.g. for use in
   * span names and span attributes. Anonymous classes are named based on their parent.
   */
  public static String simpleName(Class<?> clazz) {
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
}
