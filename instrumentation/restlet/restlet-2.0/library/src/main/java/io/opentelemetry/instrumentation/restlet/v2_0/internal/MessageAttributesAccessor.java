/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v2_0.internal;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.Nullable;
import org.restlet.Message;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class MessageAttributesAccessor {

  private static final MethodHandle GET_ATTRIBUTES;

  static {
    MethodHandle getAttributes = null;

    MethodHandles.Lookup lookup = MethodHandles.lookup();
    try {
      getAttributes =
          lookup.findVirtual(Message.class, "getAttributes", MethodType.methodType(Map.class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      // changed the return type to ConcurrentMap in version 2.1
      try {
        getAttributes =
            lookup.findVirtual(
                Message.class, "getAttributes", MethodType.methodType(ConcurrentMap.class));
      } catch (NoSuchMethodException | IllegalAccessException ex) {
        // ignored
      }
    }

    GET_ATTRIBUTES = getAttributes;
  }

  @SuppressWarnings("unchecked")
  @Nullable
  public static Map<String, Object> getAttributes(Message message) {
    if (GET_ATTRIBUTES == null) {
      return null;
    }
    try {
      Map<String, Object> attributes = (Map<String, Object>) GET_ATTRIBUTES.invoke(message);
      return attributes;
    } catch (Throwable e) {
      return null;
    }
  }

  private MessageAttributesAccessor() {}
}
