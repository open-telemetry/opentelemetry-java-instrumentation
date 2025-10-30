/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.testing;

import java.net.URL;
import java.net.URLClassLoader;

public class TestClassLoader extends URLClassLoader {

  public TestClassLoader(ClassLoader parent) {
    super(
        new URL[] {TestClassLoader.class.getProtectionDomain().getCodeSource().getLocation()},
        parent);
  }

  @Override
  protected synchronized Class<?> loadClass(String name, boolean resolve)
      throws ClassNotFoundException {
    Class<?> clazz = findLoadedClass(name);
    if (clazz != null) {
      return clazz;
    }
    if (name.startsWith("io.opentelemetry.instrumentation.jdbc.testing")) {
      try {
        return findClass(name);
      } catch (ClassNotFoundException exception) {
        // ignore
      }
    }
    return super.loadClass(name, resolve);
  }
}
