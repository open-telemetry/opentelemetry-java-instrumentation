/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import io.opentelemetry.instrumentation.api.cache.Cache;
import java.util.Arrays;

/** Helper class for detecting whether given class has virtual fields. */
public final class VirtualFieldDetector {

  private static final Cache<Class<?>, Boolean> classesWithVirtualFields = Cache.weak();

  private VirtualFieldDetector() {}

  /**
   * Detect whether given class has virtual fields. This method looks for virtual fields only from
   * the specified class not its super classes.
   *
   * @param clazz a class
   * @return true if given class has virtual fields
   */
  public static boolean hasVirtualFields(Class<?> clazz) {
    // clazz.getInterfaces() needs to be called before reading from classesWithVirtualFields
    // as the call to clazz.getInterfaces() triggers adding clazz to that map via instrumentation
    // calling VirtualFieldDetector#markVirtualFieldsPresent() from Class#getInterfaces()
    Class<?>[] interfaces = clazz.getInterfaces();
    // to avoid breaking in case internal-reflection instrumentation is disabled check whether
    // interfaces array contains virtual field marker interface
    if (Arrays.asList(interfaces).contains(VirtualFieldInstalledMarker.class)) {
      return true;
    }
    return classesWithVirtualFields.get(clazz) != null;
  }

  public static void markVirtualFieldsPresent(Class<?> clazz) {
    classesWithVirtualFields.put(clazz, Boolean.TRUE);
  }
}
