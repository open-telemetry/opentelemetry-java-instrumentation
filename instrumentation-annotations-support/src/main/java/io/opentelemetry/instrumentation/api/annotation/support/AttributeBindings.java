/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.annotation.support;

import io.opentelemetry.api.common.AttributesBuilder;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/** Represents the bindings of method parameters to attributes of a traced method. */
interface AttributeBindings {

  /**
   * Indicates that the traced method has no parameters bound to attributes.
   *
   * @return {@code true} if the traced method has no bound parameters; otherwise {@code false}
   */
  boolean isEmpty();

  /**
   * Applies the values of the method arguments as attributes on the span for the traced method.
   *
   * @param target the {@link AttributesBuilder} on which to set the attribute
   * @param args the method arguments
   */
  void apply(AttributesBuilder target, Object[] args);

  /**
   * Creates a binding of the parameters of the traced method to span attributes.
   *
   * @param method the traced method
   * @return the bindings of the parameters
   */
  static AttributeBindings bind(
      Method method, ParameterAttributeNamesExtractor parameterAttributeNamesExtractor) {
    AttributeBindings bindings = EmptyAttributeBindings.INSTANCE;

    Parameter[] parameters = method.getParameters();
    if (parameters.length == 0) {
      return bindings;
    }

    String[] attributeNames = parameterAttributeNamesExtractor.extract(method, parameters);
    if (attributeNames == null || attributeNames.length != parameters.length) {
      return bindings;
    }

    for (int i = 0; i < parameters.length; i++) {
      Parameter parameter = parameters[i];
      String attributeName = attributeNames[i];
      if (attributeName == null || attributeName.isEmpty()) {
        continue;
      }

      bindings =
          new CombinedAttributeBindings(
              bindings,
              i,
              AttributeBindingFactory.createBinding(
                  attributeName, parameter.getParameterizedType()));
    }

    return bindings;
  }
}
