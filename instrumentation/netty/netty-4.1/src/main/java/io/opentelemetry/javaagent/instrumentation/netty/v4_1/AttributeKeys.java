/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1;

import io.grpc.Context;
import io.netty.util.AttributeKey;
import io.opentelemetry.javaagent.instrumentation.api.WeakMap;
import io.opentelemetry.javaagent.instrumentation.netty.v4_1.client.HttpClientTracingHandler;
import io.opentelemetry.javaagent.instrumentation.netty.v4_1.server.HttpServerTracingHandler;
import io.opentelemetry.trace.Span;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class AttributeKeys {
  private static final WeakMap<ClassLoader, ConcurrentMap<String, AttributeKey<?>>> map =
      WeakMap.Implementation.DEFAULT.get();
  private static final WeakMap.ValueSupplier<ClassLoader, ConcurrentMap<String, AttributeKey<?>>>
      mapSupplier =
          new WeakMap.ValueSupplier<ClassLoader, ConcurrentMap<String, AttributeKey<?>>>() {
            @Override
            public ConcurrentMap<String, AttributeKey<?>> get(ClassLoader ignore) {
              return new ConcurrentHashMap<>();
            }
          };

  public static final AttributeKey<Context> PARENT_CONNECT_CONTEXT_ATTRIBUTE_KEY =
      attributeKey("io.opentelemetry.javaagent.instrumentation.netty.v4_1.parent.connect.context");

  /**
   * This constant is copied over to
   * io.opentelemetry.javaagent.instrumentation.ratpack.server.TracingHandler, so if this changes,
   * that must also change.
   */
  public static final AttributeKey<Context> SERVER_ATTRIBUTE_KEY =
      attributeKey(HttpServerTracingHandler.class.getName() + ".context");

  // TODO understand and change to context
  public static final AttributeKey<Span> CLIENT_ATTRIBUTE_KEY =
      attributeKey(HttpClientTracingHandler.class.getName() + ".span");

  public static final AttributeKey<Context> CLIENT_PARENT_ATTRIBUTE_KEY =
      attributeKey(HttpClientTracingHandler.class.getName() + ".parent");

  /**
   * Generate an attribute key or reuse the one existing in the global app map. This implementation
   * creates attributes only once even if the current class is loaded by several class loaders and
   * prevents an issue with Apache Atlas project were this class loaded by multiple class loaders,
   * while the Attribute class is loaded by a third class loader and used internally for the
   * cassandra driver.
   */
  private static <T> AttributeKey<T> attributeKey(String key) {
    ConcurrentMap<String, AttributeKey<?>> classLoaderMap =
        map.computeIfAbsent(AttributeKey.class.getClassLoader(), mapSupplier);
    if (classLoaderMap.containsKey(key)) {
      return (AttributeKey<T>) classLoaderMap.get(key);
    }

    AttributeKey<T> value = AttributeKey.valueOf(key);
    classLoaderMap.put(key, value);
    return value;
  }
}
