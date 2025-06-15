/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Function;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ServiceLoaderUtil {

  private static Function<Class<?>, List<?>> loaderFunction =
      clazz -> {
        List<Object> instances = new ArrayList<>();
        ServiceLoader<?> serviceLoader = ServiceLoader.load(clazz);
        for (Object instance : serviceLoader) {
          instances.add(instance);
        }
        return instances;
      };

  private ServiceLoaderUtil() {
    // Utility class, no instantiation
  }

  @SuppressWarnings("unchecked")
  public static <T> List<T> load(Class<T> clazz) {
    return (List<T>) loaderFunction.apply(clazz);
  }

  public static void setLoaderFunction(Function<Class<?>, List<?>> customLoaderFunction) {
    loaderFunction = customLoaderFunction;
  }
}
