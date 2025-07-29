/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.util;

import io.opentelemetry.instrumentation.api.internal.ClassNames;
import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** A utility class used to generate span names. */
public final class SpanNames {

  private static final Cache<Class<?>, Map<String, String>> spanNameCaches = Cache.weak();

  /**
   * This method is used to generate a span name based on a method. Anonymous classes are named
   * based on their parent.
   */
  public static String fromMethod(Method method) {
    return fromMethod(method.getDeclaringClass(), method.getName());
  }

  /**
   * This method is used to generate a span name based on a method. Anonymous classes are named
   * based on their parent.
   */
  public static String fromMethod(Class<?> clazz, String methodName) {
    // the cache (ConcurrentHashMap) is naturally bounded by the number of methods in a class
    Map<String, String> spanNameCache =
        spanNameCaches.computeIfAbsent(clazz, c -> new ConcurrentHashMap<>());

    // not using computeIfAbsent, because it would require a capturing (allocating) lambda
    String spanName = spanNameCache.get(methodName);
    if (spanName != null) {
      return spanName;
    }
    spanName = ClassNames.simpleName(clazz) + "." + methodName;
    spanNameCache.put(methodName, spanName);
    return spanName;
  }

  private SpanNames() {}
}
