/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import javax.annotation.Nullable;

final class CallbackClassLoader {

  @Nullable
  static ClassLoader find(Object... owners) {
    ClassLoader instrumentationClassLoader = CallbackClassLoader.class.getClassLoader();
    ClassLoader selected = null;
    int selectedDepth = -1;
    for (Object owner : owners) {
      if (owner == null) {
        continue;
      }
      ClassLoader classLoader = owner.getClass().getClassLoader();
      if (classLoader == null || classLoader == instrumentationClassLoader) {
        continue;
      }
      int depth = 0;
      for (ClassLoader parent = classLoader; parent != null; parent = parent.getParent()) {
        depth++;
      }
      if (depth > selectedDepth) {
        selected = classLoader;
        selectedDepth = depth;
      }
    }
    return selected;
  }

  private CallbackClassLoader() {}
}
