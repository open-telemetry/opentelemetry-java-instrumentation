/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.api.common.AttributeKey;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;

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
      return (setter, arg) -> setter.setAttribute(key, (String) arg);
    }
    if (type == long.class || type == Long.class) {
      AttributeKey<Long> key = AttributeKey.longKey(name);
      return (setter, arg) -> setter.setAttribute(key, (Long) arg);
    }
    if (type == double.class || type == Double.class) {
      AttributeKey<Double> key = AttributeKey.doubleKey(name);
      return (setter, arg) -> setter.setAttribute(key, (Double) arg);
    }
    if (type == boolean.class || type == Boolean.class) {
      AttributeKey<Boolean> key = AttributeKey.booleanKey(name);
      return (setter, arg) -> setter.setAttribute(key, (Boolean) arg);
    }
    if (type == int.class || type == Integer.class) {
      AttributeKey<Long> key = AttributeKey.longKey(name);
      return (setter, arg) -> setter.setAttribute(key, ((Integer) arg).longValue());
    }
    if (type == float.class || type == Float.class) {
      AttributeKey<Double> key = AttributeKey.doubleKey(name);
      return (setter, arg) -> setter.setAttribute(key, ((Float) arg).doubleValue());
    }

    // Simple array attribute types without conversion
    if (type == String[].class) {
      AttributeKey<List<String>> key = AttributeKey.stringArrayKey(name);
      return (setter, arg) -> setter.setAttribute(key, Arrays.asList((String[]) arg));
    }
    if (type == Long[].class) {
      AttributeKey<List<Long>> key = AttributeKey.longArrayKey(name);
      return (setter, arg) -> setter.setAttribute(key, Arrays.asList((Long[]) arg));
    }
    if (type == Double[].class) {
      AttributeKey<List<Double>> key = AttributeKey.doubleArrayKey(name);
      return (setter, arg) -> setter.setAttribute(key, Arrays.asList((Double[]) arg));
    }
    if (type == Boolean[].class) {
      AttributeKey<List<Boolean>> key = AttributeKey.booleanArrayKey(name);
      return (setter, arg) -> setter.setAttribute(key, Arrays.asList((Boolean[]) arg));
    }

    // TODO: long[], int[], float[], boolean[], double[], List<String>, List<Integer>, List<Long>,
    // List<Boolean>, EnumSet<?>

    return defaultBinding(name);
  }

  private static AttributeBinding defaultBinding(String name) {
    AttributeKey<String> key = AttributeKey.stringKey(name);
    return (setter, arg) -> setter.setAttribute(key, arg.toString());
  }
}
