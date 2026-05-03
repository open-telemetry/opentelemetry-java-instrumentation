/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal.cache.weaklockfree;

import javax.annotation.Nullable;

class ThreadUtil {

  @Nullable private static final Class<?> virtualThreadClass = findVirtualThreadClass();

  @Nullable
  private static Class<?> findVirtualThreadClass() {
    try {
      return Class.forName("java.lang.BaseVirtualThread");
    } catch (ClassNotFoundException e) {
      return null;
    }
  }

  static boolean isVirtualThread(Thread thread) {
    return virtualThreadClass != null && virtualThreadClass.isInstance(thread);
  }

  private ThreadUtil() {}
}
