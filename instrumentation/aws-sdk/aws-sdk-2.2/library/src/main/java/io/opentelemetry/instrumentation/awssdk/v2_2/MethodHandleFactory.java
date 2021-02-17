/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.ConcurrentHashMap;

class MethodHandleFactory {

  private final ClassValue<ConcurrentHashMap<String, MethodHandle>> getterCache =
      new ClassValue<ConcurrentHashMap<String, MethodHandle>>() {
        @Override
        protected ConcurrentHashMap<String, MethodHandle> computeValue(Class<?> type) {
          return new ConcurrentHashMap<>();
        }
      };

  MethodHandle forField(Class clazz, String fieldName)
      throws NoSuchMethodException, IllegalAccessException {
    MethodHandle methodHandle = getterCache.get(clazz).get(fieldName);
    if (methodHandle == null) {
      methodHandle = MethodHandles.publicLookup().unreflect(clazz.getMethod(fieldName));
      getterCache.get(clazz).put(fieldName, methodHandle);
    }
    return methodHandle;
  }
}
