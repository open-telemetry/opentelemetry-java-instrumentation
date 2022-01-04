/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.reflection;

import io.opentelemetry.javaagent.bootstrap.VirtualFieldAccessorMarker;
import io.opentelemetry.javaagent.bootstrap.VirtualFieldInstalledMarker;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class ReflectionHelper {

  private ReflectionHelper() {}

  public static Field[] filterFields(Class<?> containingClass, Field[] fields) {
    if (fields.length == 0
        || !VirtualFieldInstalledMarker.class.isAssignableFrom(containingClass)) {
      // nothing to filter when class does not have any added virtual fields
      return fields;
    }
    List<Field> result = new ArrayList<>(fields.length);
    for (Field field : fields) {
      // virtual fields are marked as synthetic
      if (field.isSynthetic() && field.getName().startsWith("__opentelemetryVirtualField$")) {
        continue;
      }
      result.add(field);
    }
    return result.toArray(new Field[0]);
  }

  public static Method[] filterMethods(Class<?> containingClass, Method[] methods) {
    if (methods.length == 0
        || !VirtualFieldInstalledMarker.class.isAssignableFrom(containingClass)) {
      // nothing to filter when class does not have any added virtual fields
      return methods;
    }
    List<Method> result = new ArrayList<>(methods.length);
    for (Method method : methods) {
      // virtual field accessor methods are marked as synthetic
      if (method.isSynthetic()
          && (method.getName().startsWith("__get__opentelemetryVirtualField$")
              || method.getName().startsWith("__set__opentelemetryVirtualField$"))) {
        continue;
      }
      result.add(method);
    }
    return result.toArray(new Method[0]);
  }

  @SuppressWarnings("unused")
  public static Class<?>[] filterInterfaces(Class<?>[] interfaces, Class<?> containingClass) {
    if (interfaces.length == 0
        || !VirtualFieldInstalledMarker.class.isAssignableFrom(containingClass)) {
      // nothing to filter when class does not have any added virtual fields
      return interfaces;
    }
    List<Class<?>> result = new ArrayList<>(interfaces.length);
    for (Class<?> interfaceClass : interfaces) {
      // filter out virtual field marker and accessor interfaces
      if (interfaceClass == VirtualFieldInstalledMarker.class
          || (VirtualFieldAccessorMarker.class.isAssignableFrom(interfaceClass)
              && interfaceClass.isSynthetic()
              && interfaceClass.getName().contains("VirtualFieldAccessor$"))) {
        continue;
      }
      result.add(interfaceClass);
    }
    if (result.size() != interfaces.length) {
      // for classes that have removed interfaces remember what they really have
      RealInterfaces.set(containingClass, interfaces);
    }
    return result.toArray(new Class<?>[0]);
  }
}
