/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import java.util.Collection;

/** Helper class for detecting whether given class has virtual fields. */
public final class VirtualFieldDetector {

  // class to virtual field interface dot class names (see
  // GeneratedVirtualFieldNames.getFieldAccessorInterfaceName)
  private static final Cache<Class<?>, Collection<String>> classesWithVirtualFields = Cache.weak();

  private VirtualFieldDetector() {}

  /**
   * Detect whether given class has given virtual field. This method looks for virtual fields only
   * from the specified class not its super classes.
   *
   * @param clazz a class
   * @param virtualFieldInterfaceClassName virtual field interface class dot name
   * @return true if given class has the specified virtual field
   */
  public static boolean hasVirtualField(Class<?> clazz, String virtualFieldInterfaceClassName) {
    if (!VirtualFieldInstalledMarker.class.isAssignableFrom(clazz)) {
      return false;
    }
    // clazz.getInterfaces() needs to be called before reading from classesWithVirtualFields
    // as the call to clazz.getInterfaces() triggers adding clazz to that map via instrumentation
    // calling VirtualFieldDetector#markVirtualFields() from Class#getInterfaces()
    Class<?>[] interfaces = clazz.getInterfaces();
    // to avoid breaking in case internal-reflection instrumentation is disabled check whether
    // interfaces array contains virtual field marker interface
    for (Class<?> interfaceClass : interfaces) {
      if (virtualFieldInterfaceClassName.equals(interfaceClass.getName())) {
        return true;
      }
    }

    Collection<String> virtualFields = classesWithVirtualFields.get(clazz);
    return virtualFields != null && virtualFields.contains(virtualFieldInterfaceClassName);
  }

  public static void markVirtualFields(Class<?> clazz, Collection<String> virtualFieldClassName) {
    classesWithVirtualFields.put(clazz, virtualFieldClassName);
  }
}
