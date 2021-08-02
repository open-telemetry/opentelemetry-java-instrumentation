/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.annotation.support;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.caching.Cache;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Extractor of {@link io.opentelemetry.api.common.Attributes} for a traced method. */
public final class MethodSpanAttributesExtractor<REQUEST, RESPONSE>
    extends AttributesExtractor<REQUEST, RESPONSE> {

  private final BaseAttributeBinder binder;
  private final MethodExtractor<REQUEST> methodExtractor;
  private final MethodArgumentsExtractor<REQUEST> methodArgumentsExtractor;
  private final Cache<Method, AttributeBindings> cache;

  public static <REQUEST, RESPONSE> MethodSpanAttributesExtractor<REQUEST, RESPONSE> newInstance(
      MethodExtractor<REQUEST> methodExtractor,
      ParameterAttributeNamesExtractor parameterAttributeNamesExtractor,
      MethodArgumentsExtractor<REQUEST> methodArgumentsExtractor) {

    return new MethodSpanAttributesExtractor<>(
        methodExtractor, parameterAttributeNamesExtractor, methodArgumentsExtractor);
  }

  MethodSpanAttributesExtractor(
      MethodExtractor<REQUEST> methodExtractor,
      ParameterAttributeNamesExtractor parameterAttributeNamesExtractor,
      MethodArgumentsExtractor<REQUEST> methodArgumentsExtractor) {
    this.methodExtractor = methodExtractor;
    this.methodArgumentsExtractor = methodArgumentsExtractor;
    this.binder = new MethodSpanAttributeBinder(parameterAttributeNamesExtractor);
    this.cache = new MethodCache<>();
  }

  @Override
  protected void onStart(AttributesBuilder attributes, REQUEST request) {
    Method method = methodExtractor.extract(request);
    AttributeBindings bindings = cache.computeIfAbsent(method, binder::bind);
    if (!bindings.isEmpty()) {
      Object[] args = methodArgumentsExtractor.extract(request);
      bindings.apply(attributes::put, args);
    }
  }

  @Override
  protected void onEnd(
      AttributesBuilder attributes, REQUEST request, @Nullable RESPONSE response) {}

  private static class MethodSpanAttributeBinder extends BaseAttributeBinder {
    private final ParameterAttributeNamesExtractor parameterAttributeNamesExtractor;

    public MethodSpanAttributeBinder(
        ParameterAttributeNamesExtractor parameterAttributeNamesExtractor) {
      this.parameterAttributeNamesExtractor = parameterAttributeNamesExtractor;
    }

    @Override
    protected @Nullable String[] attributeNamesForParameters(
        Method method, Parameter[] parameters) {
      return parameterAttributeNamesExtractor.extract(method, parameters);
    }
  }
}
