/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.common.client;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

public class MethodAccessor {

  private MethodAccessor() {}

  public static Set<String> voidMethodNames(String serviceName) {
    Set<String> methodNames = new HashSet<>();
    try {
      Class<?> clazz = Class.forName(serviceName);
      Method[] declaredMethods = clazz.getDeclaredMethods();
      for (Method declaredMethod : declaredMethods) {
        if (declaredMethod.getReturnType() == void.class) {
          methodNames.add(declaredMethod.getName());
        }
      }
    } catch (ClassNotFoundException ignore) {
      // ignore
    }
    return methodNames;
  }
}
