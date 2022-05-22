/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0;

import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.netty.v4.common.HttpRequestAndChannel;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class AttributeKeys {

  private static final ClassValue<ConcurrentMap<String, AttributeKey<?>>> mapSupplier =
      new ClassValue<ConcurrentMap<String, AttributeKey<?>>>() {
        @Override
        protected ConcurrentMap<String, AttributeKey<?>> computeValue(Class<?> type) {
          return new ConcurrentHashMap<>();
        }
      };

  public static final AttributeKey<Context> WRITE_CONTEXT =
      attributeKey(AttributeKeys.class.getName() + ".write-context");

  // this is the context that has the server span
  public static final AttributeKey<Context> SERVER_CONTEXT =
      attributeKey(AttributeKeys.class.getName() + ".server-context");

  public static final AttributeKey<HttpRequestAndChannel> SERVER_REQUEST =
      attributeKey(AttributeKeys.class.getName() + ".http-server-request");

  public static final AttributeKey<Context> CLIENT_CONTEXT =
      attributeKey(AttributeKeys.class.getName() + ".client-context");

  public static final AttributeKey<Context> CLIENT_PARENT_CONTEXT =
      attributeKey(AttributeKeys.class.getName() + ".client-parent-context");

  public static final AttributeKey<HttpRequestAndChannel> CLIENT_REQUEST =
      attributeKey(AttributeKeys.class.getName() + ".http-client-request");

  public static final AttributeKey<HttpResponse> CLIENT_RESPONSE =
      attributeKey(AttributeKeys.class.getName() + ".http-client-response");

  /**
   * Generate an attribute key or reuse the one existing in the global app map. This implementation
   * creates attributes only once even if the current class is loaded by several class loaders and
   * prevents an issue with Apache Atlas project were this class loaded by multiple class loaders,
   * while the Attribute class is loaded by a third class loader and used internally for the
   * cassandra driver.
   *
   * <p>Keep this API public for vendor instrumentations
   */
  @SuppressWarnings("unchecked")
  public static <T> AttributeKey<T> attributeKey(String key) {
    ConcurrentMap<String, AttributeKey<?>> classLoaderMap = mapSupplier.get(AttributeKey.class);
    return (AttributeKey<T>) classLoaderMap.computeIfAbsent(key, AttributeKey::new);
  }
}
