/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.instrumentation.netty.v4_0;

import io.grpc.Context;
import io.netty.util.AttributeKey;
import io.opentelemetry.auto.bootstrap.WeakMap;
import io.opentelemetry.auto.instrumentation.netty.v4_0.client.HttpClientTracingHandler;
import io.opentelemetry.auto.instrumentation.netty.v4_0.server.HttpServerTracingHandler;
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
            public ConcurrentMap<String, AttributeKey<?>> get(final ClassLoader ignore) {
              return new ConcurrentHashMap<>();
            }
          };

  public static final AttributeKey<Span> PARENT_CONNECT_SPAN_ATTRIBUTE_KEY =
      attributeKey("io.opentelemetry.auto.instrumentation.netty.v4_0.parent.connect.span");

  public static final AttributeKey<Context> SERVER_ATTRIBUTE_KEY =
      attributeKey(HttpServerTracingHandler.class.getName() + ".span");

  public static final AttributeKey<Span> CLIENT_ATTRIBUTE_KEY =
      attributeKey(HttpClientTracingHandler.class.getName() + ".span");

  public static final AttributeKey<Span> CLIENT_PARENT_ATTRIBUTE_KEY =
      attributeKey(HttpClientTracingHandler.class.getName() + ".parent");

  /**
   * Generate an attribute key or reuse the one existing in the global app map. This implementation
   * creates attributes only once even if the current class is loaded by several class loaders and
   * prevents an issue with Apache Atlas project were this class loaded by multiple class loaders,
   * while the Attribute class is loaded by a third class loader and used internally for the
   * cassandra driver.
   */
  private static <T> AttributeKey<T> attributeKey(final String key) {
    final ConcurrentMap<String, AttributeKey<?>> classLoaderMap =
        map.computeIfAbsent(AttributeKey.class.getClassLoader(), mapSupplier);
    if (classLoaderMap.containsKey(key)) {
      return (AttributeKey<T>) classLoaderMap.get(key);
    }

    final AttributeKey<T> value = new AttributeKey<>(key);
    classLoaderMap.put(key, value);
    return value;
  }
}
