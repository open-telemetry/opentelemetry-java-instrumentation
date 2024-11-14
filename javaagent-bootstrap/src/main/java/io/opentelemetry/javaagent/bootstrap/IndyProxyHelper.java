/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

public class IndyProxyHelper {

  private IndyProxyHelper() {}

  public static <T> T unwrapIfNeeded(Object o, Class<T> type) {
    if (type.isAssignableFrom(o.getClass())) {
      return type.cast(o);
    }

    if (o instanceof IndyProxy) {
      Object delegate = ((IndyProxy) o).__getIndyProxyDelegate();
      if (type.isAssignableFrom(delegate.getClass())) {
        return type.cast(delegate);
      }
    }

    throw new IllegalArgumentException("unexpected object type");
  }
}
