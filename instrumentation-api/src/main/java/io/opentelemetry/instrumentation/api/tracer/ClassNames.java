/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

public final class ClassNames {

  private static final ClassValue<String> simpleNames =
      new ClassValue<String>() {
        @Override
        protected String computeValue(Class<?> type) {
          if (!type.isAnonymousClass()) {
            return type.getSimpleName();
          }
          String className = type.getName();
          if (type.getPackage() != null) {
            String pkgName = type.getPackage().getName();
            if (!pkgName.isEmpty()) {
              className = className.substring(pkgName.length() + 1);
            }
          }
          return className;
        }
      };

  /**
   * This method is used to generate a simple name based on a given class reference, e.g. for use in
   * span names and span attributes. Anonymous classes are named based on their parent.
   */
  public static String simpleName(Class<?> type) {
    return simpleNames.get(type);
  }

  private ClassNames() {}
}
