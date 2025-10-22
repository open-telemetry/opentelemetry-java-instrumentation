/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.util;

import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import io.opentelemetry.javaagent.tooling.HelperInjector;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Ownership;
import net.bytebuddy.description.modifier.Visibility;

class ClassLoaderMap {
  private static final Cache<ClassLoader, WeakReference<Map<Object, Object>>> data = Cache.weak();
  private static final Map<Object, Object> bootLoaderData = new ConcurrentHashMap<>();

  public static Object get(ClassLoader classLoader, Object key) {
    return getClassLoaderData(classLoader, false).get(key);
  }

  public static void put(ClassLoader classLoader, Object key, Object value) {
    getClassLoaderData(classLoader, true).put(key, value);
  }

  public static Object computeIfAbsent(
      ClassLoader classLoader, Object key, Supplier<? extends Object> value) {
    return getClassLoaderData(classLoader, true).computeIfAbsent(key, unused -> value.get());
  }

  private static Map<Object, Object> getClassLoaderData(
      ClassLoader classLoader, boolean initialize) {
    if (classLoader == null) {
      return bootLoaderData;
    }

    WeakReference<Map<Object, Object>> weakReference = data.get(classLoader);
    Map<Object, Object> map = weakReference != null ? weakReference.get() : null;
    if (map == null) {
      // skip setting up the map if get was called
      if (!initialize) {
        return Collections.emptyMap();
      }
      map = createMap(classLoader);
      data.put(classLoader, new WeakReference<>(map));
    }
    return map;
  }

  @SuppressWarnings("unchecked")
  private static Map<Object, Object> createMap(ClassLoader classLoader) {
    String className =
        "io.opentelemetry.javaagent.ClassLoaderData$$"
            + Integer.toHexString(System.identityHashCode(classLoader));
    // generate a class with a single static field named "data" and define it in the given class
    // loader
    byte[] bytes =
        new ByteBuddy()
            .subclass(Object.class)
            .name(className)
            .defineField("data", Object.class, Ownership.STATIC, Visibility.PUBLIC)
            .make()
            .getBytes();
    HelperInjector.injectHelperClasses(
        classLoader, Collections.singletonMap(className, () -> bytes));
    Map<Object, Object> map;
    try {
      Class<?> clazz = Class.forName(className, false, classLoader);

      Field field = clazz.getField("data");
      synchronized (classLoader) {
        map = (Map<Object, Object>) field.get(classLoader);
        if (map == null) {
          map = new ConcurrentHashMap<>();
          field.set(null, map);
        }
      }
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
    return map;
  }

  private ClassLoaderMap() {}
}
