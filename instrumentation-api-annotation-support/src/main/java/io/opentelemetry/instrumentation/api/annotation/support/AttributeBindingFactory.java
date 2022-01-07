/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.annotation.support;

import io.opentelemetry.api.common.AttributeKey;
import java.lang.reflect.Type;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Helper class for creating {@link AttributeBinding} instances based on the {@link Type} of the
 * parameter for a traced method.
 */
class AttributeBindingFactory {
  private AttributeBindingFactory() {}

  static AttributeBinding createBinding(String name, Type type) {

    // Simple scalar parameter types
    if (type == String.class) {
      AttributeKey<String> key = AttributeKey.stringKey(name);
      return (setter, arg) -> setter.put(key, (String) arg);
    }
    if (type == long.class || type == Long.class) {
      AttributeKey<Long> key = AttributeKey.longKey(name);
      return (setter, arg) -> setter.put(key, (Long) arg);
    }
    if (type == double.class || type == Double.class) {
      AttributeKey<Double> key = AttributeKey.doubleKey(name);
      return (setter, arg) -> setter.put(key, (Double) arg);
    }
    if (type == boolean.class || type == Boolean.class) {
      AttributeKey<Boolean> key = AttributeKey.booleanKey(name);
      return (setter, arg) -> setter.put(key, (Boolean) arg);
    }
    if (type == int.class || type == Integer.class) {
      AttributeKey<Long> key = AttributeKey.longKey(name);
      return (setter, arg) -> setter.put(key, ((Integer) arg).longValue());
    }
    if (type == float.class || type == Float.class) {
      AttributeKey<Double> key = AttributeKey.doubleKey(name);
      return (setter, arg) -> setter.put(key, ((Float) arg).doubleValue());
    }

    if (isArrayType(type)) {
      return arrayBinding(name, type);
    }

    return resolveListComponentType(type)
        .map(componentType -> listBinding(name, componentType))
        .orElseGet(() -> defaultBinding(name));
  }

  private static boolean isArrayType(Type type) {
    if (type instanceof Class) {
      return ((Class<?>) type).isArray();
    }
    return false;
  }

  private static Optional<Type> resolveListComponentType(Type type) {
    return ParameterizedClass.of(type)
        .findParameterizedSuperclass(List.class)
        .map(pc -> pc.getActualTypeArguments()[0]);
  }

  private static AttributeBinding arrayBinding(String name, Type type) {
    // Simple array attribute types without conversion
    if (type == String[].class) {
      AttributeKey<List<String>> key = AttributeKey.stringArrayKey(name);
      return (setter, arg) -> setter.put(key, Arrays.asList((String[]) arg));
    }
    if (type == Long[].class) {
      AttributeKey<List<Long>> key = AttributeKey.longArrayKey(name);
      return (setter, arg) -> setter.put(key, Arrays.asList((Long[]) arg));
    }
    if (type == Double[].class) {
      AttributeKey<List<Double>> key = AttributeKey.doubleArrayKey(name);
      return (setter, arg) -> setter.put(key, Arrays.asList((Double[]) arg));
    }
    if (type == Boolean[].class) {
      AttributeKey<List<Boolean>> key = AttributeKey.booleanArrayKey(name);
      return (setter, arg) -> setter.put(key, Arrays.asList((Boolean[]) arg));
    }

    if (type == long[].class) {
      return longArrayBinding(name);
    }
    if (type == int[].class) {
      return intArrayBinding(name);
    }
    if (type == float[].class) {
      return floatArrayBinding(name);
    }
    if (type == double[].class) {
      return doubleArrayBinding(name);
    }
    if (type == boolean[].class) {
      return booleanArrayBinding(name);
    }
    if (type == Integer[].class) {
      return boxedIntegerArrayBinding(name);
    }
    if (type == Float[].class) {
      return boxedFloatArrayBinding(name);
    }

    return defaultArrayBinding(name);
  }

  @SuppressWarnings("unchecked")
  private static AttributeBinding listBinding(String name, Type componentType) {
    if (componentType == String.class) {
      AttributeKey<List<String>> key = AttributeKey.stringArrayKey(name);
      return (setter, arg) -> setter.put(key, (List<String>) arg);
    }
    if (componentType == Long.class) {
      AttributeKey<List<Long>> key = AttributeKey.longArrayKey(name);
      return (setter, arg) -> setter.put(key, (List<Long>) arg);
    }
    if (componentType == Double.class) {
      AttributeKey<List<Double>> key = AttributeKey.doubleArrayKey(name);
      return (setter, arg) -> setter.put(key, (List<Double>) arg);
    }
    if (componentType == Boolean.class) {
      AttributeKey<List<Boolean>> key = AttributeKey.booleanArrayKey(name);
      return (setter, arg) -> setter.put(key, (List<Boolean>) arg);
    }
    if (componentType == Integer.class) {
      AttributeKey<List<Long>> key = AttributeKey.longArrayKey(name);
      return mappedListBinding(Integer.class, key, Integer::longValue);
    }
    if (componentType == Float.class) {
      AttributeKey<List<Double>> key = AttributeKey.doubleArrayKey(name);
      return mappedListBinding(Float.class, key, Float::doubleValue);
    }

    return defaultListBinding(name);
  }

  private static AttributeBinding intArrayBinding(String name) {
    AttributeKey<List<Long>> key = AttributeKey.longArrayKey(name);
    return (setter, arg) -> {
      int[] array = (int[]) arg;
      List<Long> wrapper =
          new AbstractList<Long>() {
            @Override
            public Long get(int index) {
              return (long) array[index];
            }

            @Override
            public int size() {
              return array.length;
            }
          };
      setter.put(key, wrapper);
    };
  }

  private static AttributeBinding boxedIntegerArrayBinding(String name) {
    AttributeKey<List<Long>> key = AttributeKey.longArrayKey(name);
    return (setter, arg) -> {
      Integer[] array = (Integer[]) arg;
      List<Long> wrapper =
          new AbstractList<Long>() {
            @Override
            public Long get(int index) {
              Integer value = array[index];
              return value != null ? value.longValue() : null;
            }

            @Override
            public int size() {
              return array.length;
            }
          };
      setter.put(key, wrapper);
    };
  }

  private static AttributeBinding longArrayBinding(String name) {
    AttributeKey<List<Long>> key = AttributeKey.longArrayKey(name);
    return (setter, arg) -> {
      long[] array = (long[]) arg;
      List<Long> wrapper =
          new AbstractList<Long>() {
            @Override
            public Long get(int index) {
              return array[index];
            }

            @Override
            public int size() {
              return array.length;
            }
          };
      setter.put(key, wrapper);
    };
  }

  private static AttributeBinding floatArrayBinding(String name) {
    AttributeKey<List<Double>> key = AttributeKey.doubleArrayKey(name);
    return (setter, arg) -> {
      float[] array = (float[]) arg;
      List<Double> wrapper =
          new AbstractList<Double>() {
            @Override
            public Double get(int index) {
              return (double) array[index];
            }

            @Override
            public int size() {
              return array.length;
            }
          };
      setter.put(key, wrapper);
    };
  }

  private static AttributeBinding boxedFloatArrayBinding(String name) {
    AttributeKey<List<Double>> key = AttributeKey.doubleArrayKey(name);
    return (setter, arg) -> {
      Float[] array = (Float[]) arg;
      List<Double> wrapper =
          new AbstractList<Double>() {
            @Override
            public Double get(int index) {
              Float value = array[index];
              return value != null ? value.doubleValue() : null;
            }

            @Override
            public int size() {
              return array.length;
            }
          };
      setter.put(key, wrapper);
    };
  }

  private static AttributeBinding doubleArrayBinding(String name) {
    AttributeKey<List<Double>> key = AttributeKey.doubleArrayKey(name);
    return (setter, arg) -> {
      double[] array = (double[]) arg;
      List<Double> wrapper =
          new AbstractList<Double>() {
            @Override
            public Double get(int index) {
              return array[index];
            }

            @Override
            public int size() {
              return array.length;
            }
          };
      setter.put(key, wrapper);
    };
  }

  private static AttributeBinding booleanArrayBinding(String name) {
    AttributeKey<List<Boolean>> key = AttributeKey.booleanArrayKey(name);
    return (setter, arg) -> {
      boolean[] array = (boolean[]) arg;
      List<Boolean> wrapper =
          new AbstractList<Boolean>() {
            @Override
            public Boolean get(int index) {
              return array[index];
            }

            @Override
            public int size() {
              return array.length;
            }
          };
      setter.put(key, wrapper);
    };
  }

  private static AttributeBinding defaultArrayBinding(String name) {
    AttributeKey<List<String>> key = AttributeKey.stringArrayKey(name);
    return (setter, arg) -> {
      Object[] array = (Object[]) arg;
      List<String> wrapper =
          new AbstractList<String>() {
            @Override
            public String get(int index) {
              Object value = array[index];
              return value != null ? value.toString() : null;
            }

            @Override
            public int size() {
              return array.length;
            }
          };
      setter.put(key, wrapper);
    };
  }

  private static <T, U> AttributeBinding mappedListBinding(
      Class<T> fromClass, AttributeKey<List<U>> key, Function<T, U> mapping) {
    return (setter, arg) -> {
      @SuppressWarnings("unchecked")
      List<T> list = (List<T>) arg;
      List<U> wrapper =
          new AbstractList<U>() {
            @Override
            public U get(int index) {
              T value = list.get(index);
              return value != null ? mapping.apply(value) : null;
            }

            @Override
            public int size() {
              return list.size();
            }
          };
      setter.put(key, wrapper);
    };
  }

  private static AttributeBinding defaultListBinding(String name) {
    AttributeKey<List<String>> key = AttributeKey.stringArrayKey(name);
    return mappedListBinding(Object.class, key, Object::toString);
  }

  private static AttributeBinding defaultBinding(String name) {
    AttributeKey<String> key = AttributeKey.stringKey(name);
    return (setter, arg) -> setter.put(key, arg.toString());
  }
}
