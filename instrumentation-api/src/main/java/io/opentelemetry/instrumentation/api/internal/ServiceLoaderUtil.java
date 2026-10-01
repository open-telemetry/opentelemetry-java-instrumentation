/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static java.util.Collections.emptyList;

import java.util.ServiceLoader;
import java.util.function.Function;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ServiceLoaderUtil {

  private static volatile Function<Class<?>, Iterable<?>> loadFunction = defaultLoadFunction();

  private ServiceLoaderUtil() {}

  // we lose the generic type information because of using the loader function
  @SuppressWarnings("unchecked")
  public static <T> Iterable<T> load(Class<T> clazz) {
    return (Iterable<T>) loadFunction.apply(clazz);
  }

  public static void setLoadFunction(Function<Class<?>, Iterable<?>> customLoadFunction) {
    loadFunction = customLoadFunction;
  }

  private static Function<Class<?>, Iterable<?>> defaultLoadFunction() {
    // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/19954
    // With Android StrictMode enabled using ServiceLoader on main thread causes a
    // DiskReadViolation. We disable ServiceLoader usage by default to avoid slowing down
    // application startup.
    if ("Dalvik".equals(System.getProperty("java.vm.name"))) {
      return (unused) -> emptyList();
    }
    return ServiceLoader::load;
  }
}
