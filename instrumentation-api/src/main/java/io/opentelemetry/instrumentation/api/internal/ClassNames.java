/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import io.opentelemetry.instrumentation.api.internal.cache.Cache;

/**
 * A utility class used to compute readable simple class names.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class ClassNames {

  private static final Cache<Class<?>, String> simpleNames = Cache.weak();

  /**
   * Returns a simple class name based on a given class reference, e.g. for use in span names and
   * attributes. Anonymous classes are named based on their parent.
   */
  public static String simpleName(Class<?> type) {
    return simpleNames.computeIfAbsent(type, ClassNames::computeSimpleName);
  }

  private static String computeSimpleName(Class<?> type) {
    String className = type.getName();
    if (!type.isAnonymousClass()) {
      String simpleName = type.getSimpleName();
      // on openj9 21 simple name for lambda classes is an empty string
      if (!simpleName.isEmpty()) {
        return simpleName;
      } else {
        // handle lambda names on openj9 21
        // only lambda class names contain /
        int index = className.indexOf('/');
        if (index != -1) {
          className = className.substring(0, index);
        }
      }
    }
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
