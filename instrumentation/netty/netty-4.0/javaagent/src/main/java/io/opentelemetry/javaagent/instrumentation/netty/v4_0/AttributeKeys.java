/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0;

import io.netty.util.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.api.WeakMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class AttributeKeys {
  private static final WeakMap<ClassLoader, ConcurrentMap<String, AttributeKey<?>>> map =
      WeakMap.Implementation.DEFAULT.get();
  private static final WeakMap.ValueSupplier<ClassLoader, ConcurrentMap<String, AttributeKey<?>>>
      mapSupplier = ignore -> new ConcurrentHashMap<>();

  public static final AttributeKey<Context> CONNECT_CONTEXT =
      attributeKey(AttributeKeys.class.getName() + ".connect-context");
  public static final AttributeKey<Context> WRITE_CONTEXT =
      attributeKey(AttributeKeys.class.getName() + ".write-context");

  // this is the context that has the server span
  public static final AttributeKey<Context> SERVER_SPAN =
      attributeKey(AttributeKeys.class.getName() + ".server-span");

  public static final AttributeKey<Context> CLIENT_CONTEXT =
      attributeKey(AttributeKeys.class.getName() + ".client-context");

  public static final AttributeKey<Context> CLIENT_PARENT_CONTEXT =
      attributeKey(AttributeKeys.class.getName() + ".client-parent-context");

  /**
   * Generate an attribute key or reuse the one existing in the global app map. This implementation
   * creates attributes only once even if the current class is loaded by several class loaders and
   * prevents an issue with Apache Atlas project were this class loaded by multiple class loaders,
   * while the Attribute class is loaded by a third class loader and used internally for the
   * cassandra driver.
   *
   * <p>Keep this API public for vendor instrumentations
   */
  public static <T> AttributeKey<T> attributeKey(String key) {
    ConcurrentMap<String, AttributeKey<?>> classLoaderMap =
        map.computeIfAbsent(AttributeKey.class.getClassLoader(), mapSupplier);
    if (classLoaderMap.containsKey(key)) {
      return (AttributeKey<T>) classLoaderMap.get(key);
    }

    AttributeKey<T> value = new AttributeKey<>(key);
    classLoaderMap.put(key, value);
    return value;
  }
}
