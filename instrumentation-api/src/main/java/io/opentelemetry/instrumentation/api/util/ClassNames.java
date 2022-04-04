/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.util;

import io.opentelemetry.instrumentation.api.cache.Cache;

/** A utility class used to compute readable simple class names. */
public final class ClassNames {

  private static final Cache<Class<?>, String> simpleNames = Cache.weak();

  /**
   * Returns a simple name based on a given class reference, e.g. for use in span names and span
   * attributes. Anonymous classes are named based on their parent.
   */
  public static String simpleName(Class<?> type) {
    return simpleNames.computeIfAbsent(type, ClassNames::computeSimpleName);
  }

  private static String computeSimpleName(Class<?> type) {
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

  private ClassNames() {}
}
