/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.annotation.support;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.cache.Cache;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import javax.annotation.Nullable;

/** Extractor of {@link io.opentelemetry.api.common.Attributes} for a traced method. */
public final class MethodSpanAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  private final MethodExtractor<REQUEST> methodExtractor;
  private final MethodArgumentsExtractor<REQUEST> methodArgumentsExtractor;
  private final Cache<Method, AttributeBindings> cache;
  private final ParameterAttributeNamesExtractor parameterAttributeNamesExtractor;

  public static <REQUEST, RESPONSE> MethodSpanAttributesExtractor<REQUEST, RESPONSE> newInstance(
      MethodExtractor<REQUEST> methodExtractor,
      ParameterAttributeNamesExtractor parameterAttributeNamesExtractor,
      MethodArgumentsExtractor<REQUEST> methodArgumentsExtractor) {

    return new MethodSpanAttributesExtractor<>(
        methodExtractor,
        parameterAttributeNamesExtractor,
        methodArgumentsExtractor,
        new MethodCache<>());
  }

  MethodSpanAttributesExtractor(
      MethodExtractor<REQUEST> methodExtractor,
      ParameterAttributeNamesExtractor parameterAttributeNamesExtractor,
      MethodArgumentsExtractor<REQUEST> methodArgumentsExtractor,
      Cache<Method, AttributeBindings> cache) {
    this.methodExtractor = methodExtractor;
    this.methodArgumentsExtractor = methodArgumentsExtractor;
    this.parameterAttributeNamesExtractor = parameterAttributeNamesExtractor;
    this.cache = cache;
  }

  @Override
  public void onStart(AttributesBuilder attributes, REQUEST request) {
    Method method = methodExtractor.extract(request);
    AttributeBindings bindings = cache.computeIfAbsent(method, this::bind);
    if (!bindings.isEmpty()) {
      Object[] args = methodArgumentsExtractor.extract(request);
      bindings.apply(attributes, args);
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {}

  /**
   * Creates a binding of the parameters of the traced method to span attributes.
   *
   * @param method the traced method
   * @return the bindings of the parameters
   */
  private AttributeBindings bind(Method method) {
    AttributeBindings bindings = EmptyAttributeBindings.INSTANCE;

    Parameter[] parameters = method.getParameters();
    if (parameters.length == 0) {
      return bindings;
    }

    String[] attributeNames = parameterAttributeNamesExtractor.extract(method, parameters);
    if (attributeNames.length != parameters.length) {
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

  protected enum EmptyAttributeBindings implements AttributeBindings {
    INSTANCE;

    @Override
    public boolean isEmpty() {
      return true;
    }

    @Override
    public void apply(AttributesBuilder target, Object[] args) {}
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
    public void apply(AttributesBuilder target, Object[] args) {
      parent.apply(target, args);
      if (args != null && args.length > index) {
        Object arg = args[index];
        if (arg != null) {
          binding.apply(target, arg);
        }
      }
    }
  }
}
