/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.util;

import java.util.function.Supplier;

/**
 * Associate value with a class loader. Added value will behave as if it was stored in a field in
 * the class loader object, meaning that the value can be garbage collected once the class loader is
 * garbage collected and referencing the class loader from the value will not prevent garbage
 * collector from collecting the class loader.
 */
public final class ClassLoaderValue<T> {

  @SuppressWarnings("unchecked")
  public T get(ClassLoader classLoader) {
    return (T) ClassLoaderMap.get(classLoader, this);
  }

  public void put(ClassLoader classLoader, T value) {
    ClassLoaderMap.put(classLoader, this, value);
  }

  @SuppressWarnings("unchecked")
  public T computeIfAbsent(ClassLoader classLoader, Supplier<? extends T> value) {
    return (T) ClassLoaderMap.computeIfAbsent(classLoader, this, value);
  }
}
