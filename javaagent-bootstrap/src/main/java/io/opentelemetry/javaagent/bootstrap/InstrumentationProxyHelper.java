/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

public class InstrumentationProxyHelper {

  private InstrumentationProxyHelper() {}

  /**
   * Unwraps and casts an indy proxy, or just casts if it's not an indy proxy.
   *
   * @param <T> type of object to return
   * @param o object to unwrap
   * @param type expected object type
   * @return unwrapped proxy instance or the original object (if not a proxy) cast to the expected
   *     type
   * @throws IllegalArgumentException if the provided object the proxied object can't be cast to the
   *     expected type
   */
  public static <T> T unwrapIfNeeded(Object o, Class<T> type) {
    if (o instanceof InstrumentationProxy) {
      Object delegate = ((InstrumentationProxy) o).__getIndyProxyDelegate();
      if (type.isAssignableFrom(delegate.getClass())) {
        return type.cast(delegate);
      }
    }
    if (type.isAssignableFrom(o.getClass())) {
      return type.cast(o);
    }

    throw new IllegalArgumentException("unexpected object type");
  }
}
