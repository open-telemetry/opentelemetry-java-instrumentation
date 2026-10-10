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
 * <p>These use the generated getter / setter conventions so newly generated model nodes are handled
 * without adding node-specific bridge code.
 */
final class DeclarativeModelUtil {

  private static final ClassValue<ModelMetadata> modelMetadata =
      new ClassValue<ModelMetadata>() {
        @Override
        protected ModelMetadata computeValue(Class<?> type) {
          List<ModelProperty> properties = new ArrayList<>();
          for (Method getter : type.getMethods()) {
            if (!getter.getName().startsWith("get") || getter.getParameterCount() != 0) {
              continue;
            }
            String propertyName = getter.getName().substring("get".length());
            try {
              Method setter = type.getMethod("set" + propertyName, getter.getReturnType());
              properties.add(new ModelProperty(getter, setter, toSnakeCase(propertyName)));
            } catch (NoSuchMethodException ignored) {
              // Not a generated model property.
            }
          }
          Constructor<?> constructor = null;
          if (!properties.isEmpty()) {
            try {
              constructor = type.getConstructor();
            } catch (NoSuchMethodException ignored) {
              // Model values without a public no-arg constructor are treated as leaf values.
            }
          }
          return new ModelMetadata(properties, constructor);
        }
      };

  static void mergeDefaults(Object target, Object defaults) {
    for (ModelProperty property : modelMetadata.get(defaults.getClass()).properties) {
      Object defaultValue = invoke(property.getter, defaults);
      if (defaultValue == null) {
        continue;
      }
      Object existingValue = invoke(property.getter, target);
      if (existingValue == null) {
        if (isModel(defaultValue)) {
          Object child = newModel(defaultValue.getClass());
          invoke(property.setter, target, child);
          mergeDefaults(child, defaultValue);
        } else if (defaultValue instanceof List) {
          invoke(property.setter, target, new ArrayList<>((List<?>) defaultValue));
        } else {
          invoke(property.setter, target, defaultValue);
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
    for (ModelProperty property : modelMetadata.get(model.getClass()).properties) {
      Object value = invoke(property.getter, model);
      if (value == null) {
        continue;
      }
      String path = prefix + property.name;
      if (isModel(value)) {
        forEachLeaf(value, path + ".", consumer);
      } else {
        consumer.accept(path, value);
      }
    }
  }

  private static boolean isModel(Object value) {
    ModelMetadata metadata = modelMetadata.get(value.getClass());
    return !metadata.properties.isEmpty() && metadata.constructor != null;
  }

  private static Object newModel(Class<?> modelClass) {
    Constructor<?> constructor = modelMetadata.get(modelClass).constructor;
    if (constructor == null) {
      throw new IllegalStateException("declarative config model has no public constructor");
    }
    try {
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

  private static final class ModelMetadata {
    private final List<ModelProperty> properties;
    @Nullable private final Constructor<?> constructor;

    private ModelMetadata(List<ModelProperty> properties, @Nullable Constructor<?> constructor) {
      this.properties = properties;
      this.constructor = constructor;
    }
  }

  private static final class ModelProperty {
    private final Method getter;
    private final Method setter;
    private final String name;

    private ModelProperty(Method getter, Method setter, String name) {
      this.getter = getter;
      this.setter = setter;
      this.name = name;
    }
  }

  private DeclarativeModelUtil() {}
}
