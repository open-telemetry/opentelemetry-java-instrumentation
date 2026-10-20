/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ServiceLoaderUtil {

  @Nullable private static volatile Function<Class<?>, Iterable<?>> loadFunction;

  // we lose the generic type information because of using the loader function
  @SuppressWarnings("unchecked")
  public static <T> Iterable<T> load(Class<T> clazz) {
    Function<Class<?>, Iterable<?>> customLoadFunction = loadFunction;
    return customLoadFunction == null
        ? ServiceLoader.load(clazz)
        : (Iterable<T>) customLoadFunction.apply(clazz);
  }

  /**
   * Uses the supplier when default discovery uses the service's defining class loader. The supplier
   * must load and iterate the concrete service type using {@link ServiceLoader#load(Class,
   * ClassLoader)} so Android's R8 optimizer can replace discovery with direct instantiation.
   */
  @SuppressWarnings("unchecked")
  public static <T> Iterable<T> load(
      Class<T> clazz, Supplier<Iterable<T>> defaultClassLoaderLoadFunction) {
    Function<Class<?>, Iterable<?>> customLoadFunction = loadFunction;
    if (customLoadFunction != null) {
      return (Iterable<T>) customLoadFunction.apply(clazz);
    }
    if (Thread.currentThread().getContextClassLoader() == clazz.getClassLoader()) {
      return defaultClassLoaderLoadFunction.get();
    }
    return ServiceLoader.load(clazz);
  }

  /** Sets a custom SPI loader, or {@code null} to restore default discovery. */
  public static void setLoadFunction(@Nullable Function<Class<?>, Iterable<?>> customLoadFunction) {
    loadFunction = customLoadFunction;
  }

  private ServiceLoaderUtil() {}
}
