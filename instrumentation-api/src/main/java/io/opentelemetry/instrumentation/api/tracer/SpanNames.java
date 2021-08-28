/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.instrumentation.api.caching.Cache;
import java.lang.reflect.Method;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class SpanNames {

  private static final ClassValue<Cache<String, String>> spanNameCaches =
      new ClassValue<Cache<String, String>>() {
        @Override
        protected Cache<String, String> computeValue(Class<?> clazz) {
          // should be naturally bounded, but setting a limit just in case
          return Cache.newBuilder().setMaximumSize(100).build();
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
    Cache<String, String> spanNameCache = spanNameCaches.get(clazz);
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
