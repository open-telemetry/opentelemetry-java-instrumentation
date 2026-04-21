/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal.cache.weaklockfree;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import javax.annotation.Nullable;

class ThreadUtil {

  @Nullable private static final MethodHandle isVirtual = findIsVirtual();

  @Nullable
  private static MethodHandle findIsVirtual() {
    try {
      return MethodHandles.lookup()
          .findVirtual(Thread.class, "isVirtual", MethodType.methodType(boolean.class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      // isVirtual method is not available in Java 8
      return null;
    }
  }

  static boolean isVirtualThread(Thread thread) {
    if (isVirtual == null) {
      return false;
    }
    try {
      return (boolean) isVirtual.invoke(thread);
    } catch (Throwable t) {
      // should never happen, but if it does, we just assume it's not a virtual thread
      return false;
    }
  }

  private ThreadUtil() {}
}
