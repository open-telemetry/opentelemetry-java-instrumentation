/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.reflection;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class RealInterfaces {
  private static final Map<Class<?>, Class<?>[]> realInterfaces =
      Collections.synchronizedMap(new WeakHashMap<>());

  private RealInterfaces() {}

  /**
   * Returns the interfaces directly implemented by the given class or interface. We instrument
   * Class#getInterfaces() to remove interfaces added by us. This method can be used to get the
   * interfaces that the class directly implements including the interfaces that we have added.
   *
   * @param clazz class or interface
   * @return interfaces directly implemented by given class
   */
  public static Class<?>[] get(Class<?> clazz) {
    // instrumentation calls RealInterfaces#set() from Class#getInterfaces()
    Class<?>[] interfaces = clazz.getInterfaces();
    Class<?>[] real = realInterfaces.get(clazz);
    return real != null ? real : interfaces;
  }

  static void set(Class<?> clazz, Class<?>[] interfaces) {
    realInterfaces.put(clazz, interfaces);
  }
}
