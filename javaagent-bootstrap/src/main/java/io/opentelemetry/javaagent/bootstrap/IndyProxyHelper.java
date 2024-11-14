/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import java.lang.reflect.Field;

public class IndyProxyHelper {

  private IndyProxyHelper() {}

  public static <T> T unwrapIfNeeded(Object o, Class<T> type) {
    if (type.isAssignableFrom(o.getClass())) {
      return type.cast(o);
    }
    // public delegate field on indy proxy
    try {
      Field delegateField = o.getClass().getField("delegate");
      Object delegate = delegateField.get(o);
      if (type.isAssignableFrom(delegate.getClass())) {
        return type.cast(delegate);
      }
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new IllegalStateException(e);
    }

    return null;
  }
}
