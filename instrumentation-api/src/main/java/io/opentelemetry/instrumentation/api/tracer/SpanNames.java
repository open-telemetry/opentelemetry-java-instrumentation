/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

public final class SpanNames {

  private static final ClassValue<Map<String, String>> spanNameCaches =
      new ClassValue<Map<String, String>>() {
        @Override
        protected Map<String, String> computeValue(Class<?> clazz) {
          // the cache is naturally bounded by the number of methods in a class
          return new ConcurrentHashMap<>();
        }
      };

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
  public static String fromMethod(Class<?> clazz, @Nullable Method method) {
    return fromMethod(clazz, method == null ? "<unknown>" : method.getName());
  }

  /**
   * This method is used to generate a span name based on a method. Anonymous classes are named
   * based on their parent.
   */
  public static String fromMethod(Class<?> clazz, String methodName) {
    Map<String, String> spanNameCache = spanNameCaches.get(clazz);
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
