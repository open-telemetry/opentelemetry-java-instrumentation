/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.proxy;

import io.opentelemetry.javaagent.bootstrap.VirtualFieldInstalledMarker;
import java.util.Arrays;
import java.util.LinkedHashSet;

public class ProxyHelper {

  private static final Class<?>[] EMPTY_CLASS_ARRAY = new Class<?>[0];

  public static Class<?>[] filtered(Class<?>[] interfaces) {
    int numMarkers = 0;
    for (Class<?> iface : interfaces) {
      if (iface == VirtualFieldInstalledMarker.class) {
        numMarkers++;
      }
    }
    if (numMarkers <= 1) {
      // no duplicates, safe to use original interfaces
      return interfaces;
    }

    // it's probably ok to remove them all(?)
    // but just doing the minimum here and only removing the duplicates to prevent
    // Proxy.newProxyInstance() from throwing IllegalArgumentException: repeated interface
    return new LinkedHashSet<>(Arrays.asList(interfaces)).toArray(EMPTY_CLASS_ARRAY);
  }
}
