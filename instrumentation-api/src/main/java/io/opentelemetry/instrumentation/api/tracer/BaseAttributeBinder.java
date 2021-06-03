/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.api.common.AttributeKey;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import org.checkerframework.checker.nullness.qual.Nullable;

public abstract class BaseAttributeBinder {

  public AttributeBindings bind(Method method) {
    Parameter[] parameters = method.getParameters();
    AttributeBindings bindings = AttributeBindings.EMPTY;

    if (parameters == null || parameters.length == 0) {
      return bindings;
    }

    String[] attributeNames = attributeNamesForParameters(method, parameters);
    if (attributeNames == null || attributeNames.length != parameters.length) {
      return bindings;
    }

    for (int i = 0; i < parameters.length; i++) {
      Parameter parameter = parameters[i];
      String attributeName = attributeNames[i];
      if (attributeName == null || attributeName.isEmpty()) {
        continue;
      }

      AttributeBinding binding = creatingBinding(attributeName, parameter.getParameterizedType());
      bindings = bindings.and(i, binding);
    }

    return bindings;
  }

  @Nullable
  protected abstract String[] attributeNamesForParameters(Method method, Parameter[] parameters);

  protected AttributeBinding creatingBinding(String name, Type type) {

    // TODO: Support more attribute types based on the parameter type
    AttributeKey<String> key = AttributeKey.stringKey(name);
    return (builder, arg) -> builder.setAttribute(key, arg.toString());
  }
}
