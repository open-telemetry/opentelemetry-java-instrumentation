/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.common.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MethodAccessor {

  private static Field concreteProtocolField;
  private static final Map<String, Set<String>> SERVICE_NAME_METHOD_NAMES =
      new ConcurrentHashMap<>();

  private MethodAccessor() {}

  public static Set<String> voidMethodNames(String serviceName) {
    Set<String> exit = SERVICE_NAME_METHOD_NAMES.get(serviceName);
    if (exit != null) {
      return exit;
    }
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
