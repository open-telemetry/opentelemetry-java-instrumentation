/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.bridge;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;

/**
 * Utilities for working with generated declarative configuration model types.
 *
 * <p>These use the generated getter / wither conventions so newly generated model nodes are handled
 * without adding node-specific bridge code.
 */
final class DeclarativeModelUtil {

  static void mergeDefaults(Object target, Object defaults) {
    for (Method getter : defaults.getClass().getMethods()) {
      Method wither = findWither(defaults.getClass(), getter);
      if (wither == null) {
        continue;
      }
      Object defaultValue = invoke(getter, defaults);
      if (defaultValue == null) {
        continue;
      }
      Object existingValue = invoke(getter, target);
      if (existingValue == null) {
        if (isModel(defaultValue)) {
          Object child = newModel(defaultValue.getClass());
          invoke(wither, target, child);
          mergeDefaults(child, defaultValue);
        } else if (defaultValue instanceof List) {
          invoke(wither, target, new ArrayList<>((List<?>) defaultValue));
        } else {
          invoke(wither, target, defaultValue);
        }
      } else if (isModel(existingValue) && isModel(defaultValue)) {
        mergeDefaults(existingValue, defaultValue);
      }
    }
  }

  static void forEachLeaf(Object model, BiConsumer<String, Object> consumer) {
    forEachLeaf(model, "", consumer);
  }

  private static void forEachLeaf(
      Object model, String prefix, BiConsumer<String, Object> consumer) {
    for (Method getter : model.getClass().getMethods()) {
      if (findWither(model.getClass(), getter) == null) {
        continue;
      }
      Object value = invoke(getter, model);
      if (value == null) {
        continue;
      }
      String path = prefix + toSnakeCase(getter.getName().substring("get".length()));
      if (isModel(value)) {
        forEachLeaf(value, path + ".", consumer);
      } else {
        consumer.accept(path, value);
      }
    }
  }

  @Nullable
  private static Method findWither(Class<?> modelClass, Method getter) {
    if (!getter.getName().startsWith("get") || getter.getParameterCount() != 0) {
      return null;
    }
    String witherName = "with" + getter.getName().substring("get".length());
    try {
      return modelClass.getMethod(witherName, getter.getReturnType());
    } catch (NoSuchMethodException ignored) {
      return null;
    }
  }

  private static boolean isModel(Object value) {
    for (Method method : value.getClass().getMethods()) {
      if (findWither(value.getClass(), method) != null) {
        return true;
      }
    }
    return false;
  }

  private static Object newModel(Class<?> modelClass) {
    try {
      Constructor<?> constructor = modelClass.getConstructor();
      return constructor.newInstance();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("could not create declarative config model", e);
    }
  }

  private static Object invoke(Method method, Object target, Object... arguments) {
    try {
      return method.invoke(target, arguments);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException("could not access declarative config model", e);
    }
  }

  private static String toSnakeCase(String name) {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < name.length(); i++) {
      char character = name.charAt(i);
      if (Character.isUpperCase(character) && i > 0) {
        result.append('_');
      }
      result.append(Character.toString(character).toLowerCase(Locale.ROOT));
    }
    return result.toString();
  }

  private DeclarativeModelUtil() {}
}
