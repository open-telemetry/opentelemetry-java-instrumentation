/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.muzzle.generation;

final class Utils {

  /** com/foo/Bar to com.foo.Bar */
  static String getClassName(String internalName) {
    return internalName.replace('/', '.');
  }

  /** com.foo.Bar to com/foo/Bar */
  static String getInternalName(Class<?> clazz) {
    return clazz.getName().replace('.', '/');
  }

  /** com.foo.Bar to com/foo/Bar.class */
  static String getResourceName(String className) {
    return className.replace('.', '/') + ".class";
  }

  private Utils() {}
}
