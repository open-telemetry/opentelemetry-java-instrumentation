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

/** Base class for instrumentation-specific attribute binding for traced methods. */
public abstract class BaseAttributeBinder {

  /**
   * Creates a binding of the parameters of the traced method to span attributes.
   *
   * @param method the traced method
   * @return the bindings of the parameters
   */
  public AttributeBindings bind(Method method) {
    AttributeBindings bindings = EmptyAttributeBindings.INSTANCE;

    Parameter[] parameters = method.getParameters();
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
      bindings = new CombinedAttributeBindings(bindings, i, binding);
    }

    return bindings;
  }

  /**
   * Returns an array of the names of the attributes for the parameters of the traced method. The
   * array should be the same length as the array of the method parameters. An element may be {@code
   * null} to indicate that the parameter should not be bound to an attribute. The array may also be
   * {@code null} to indicate that the method has no parameters to bind to attributes.
   *
   * @param method the traced method
   * @param parameters the method parameters
   * @return an array of the attribute names
   */
  @Nullable
  protected abstract String[] attributeNamesForParameters(Method method, Parameter[] parameters);

  private enum EmptyAttributeBindings implements AttributeBindings {
    INSTANCE;

    @Override
    public boolean isEmpty() {
      return true;
    }

    @Override
    public void apply(AttributeSetter setter, Object[] args) {}
  }

  private static final class CombinedAttributeBindings implements AttributeBindings {
    private final AttributeBindings parent;
    private final int index;
    private final AttributeBinding binding;

    public CombinedAttributeBindings(
        AttributeBindings parent, int index, AttributeBinding binding) {
      this.parent = parent;
      this.index = index;
      this.binding = binding;
    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Override
    public void apply(AttributeSetter setter, Object[] args) {
      parent.apply(setter, args);
      if (args != null && args.length > index) {
        Object arg = args[index];
        if (arg != null) {
          binding.apply(setter, arg);
        }
      }
    }
  }

  protected static AttributeBinding creatingBinding(String name, Type type) {

    // TODO: Support more attribute types based on the parameter type
    AttributeKey<String> key = AttributeKey.stringKey(name);
    return (setter, arg) -> setter.setAttribute(key, arg.toString());
  }
}
