/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.common.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MethodAccessor {

  private static Field concreteProtocolField;
  private static final Map<String, Set<String>> SERVICE_NAME_METHOD_NAMES = new HashMap<>();

  private MethodAccessor() {}

  public static Set<String> voidMethodNames(String serviceName) {
    Set<String> methodNames = SERVICE_NAME_METHOD_NAMES.getOrDefault(serviceName, new HashSet<>());
    if (!methodNames.isEmpty()) {
      return methodNames;
    }
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
    SERVICE_NAME_METHOD_NAMES.put(serviceName, methodNames);
    return methodNames;
  }

  public static Field getConcreteProtocolField(Class<?> clazz) {
    if (concreteProtocolField != null) {
      return concreteProtocolField;
    }
    try {
      Field field = clazz.getDeclaredField("concreteProtocol");
      field.setAccessible(true);
      concreteProtocolField = field;
    } catch (NoSuchFieldException ignore) {
      // ignore
    }
    return concreteProtocolField;
  }
}
