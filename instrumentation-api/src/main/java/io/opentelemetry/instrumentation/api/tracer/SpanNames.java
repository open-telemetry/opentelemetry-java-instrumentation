/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.instrumentation.api.caching.Cache;
import io.opentelemetry.instrumentation.api.util.ClassAndMethod;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

public final class SpanNames {

  private static final Cache<Class<?>, Map<String, String>> spanNameCaches =
      Cache.builder().setWeakKeys().build();

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
  public static String fromMethod(ClassAndMethod classAndMethod) {
    return fromMethod(classAndMethod.declaringClass(), classAndMethod.methodName());
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
