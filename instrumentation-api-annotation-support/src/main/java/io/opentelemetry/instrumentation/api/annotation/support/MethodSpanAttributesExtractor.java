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
import java.util.function.Function;
import org.checkerframework.checker.nullness.qual.Nullable;

public class MethodSpanAttributesExtractor<REQUEST, RESPONSE>
    extends AttributesExtractor<REQUEST, RESPONSE> {

  private final Binder binder;
  private final Function<REQUEST, Method> methodResolver;
  private final Function<REQUEST, Object[]> argsResolver;
  private final Cache<Method, AttributeBindings> cache;
  private final ParameterAttributeNamesExtractor parameterAttributeNamesExtractor;

  public static <REQUEST, RESPONSE> MethodSpanAttributesExtractorBuilder<REQUEST, RESPONSE> builder(
      Function<REQUEST, Method> methodResolver) {
    return new MethodSpanAttributesExtractorBuilder(methodResolver);
  }

  MethodSpanAttributesExtractor(MethodSpanAttributesExtractorBuilder builder) {
    this.binder = new Binder();
    this.methodResolver = builder.methodResolver;
    this.argsResolver = builder.argsResolver;
    this.cache = builder.cache;
    this.parameterAttributeNamesExtractor = builder.parameterAttributeNamesExtractor;
  }

  @Override
  protected void onStart(AttributesBuilder attributes, REQUEST request) {
    Method method = methodResolver.apply(request);
    AttributeBindings bindings =
        cache != null ? cache.computeIfAbsent(method, binder::bind) : binder.bind(method);
    if (!bindings.isEmpty()) {
      Object[] args = argsResolver.apply(request);
      bindings.apply(attributes::put, args);
    }
  }

  @Override
  protected void onEnd(
      AttributesBuilder attributes, REQUEST request, @Nullable RESPONSE response) {}

  private class Binder extends BaseAttributeBinder {
    @Override
    protected @Nullable String[] attributeNamesForParameters(
        Method method, Parameter[] parameters) {
      return parameterAttributeNamesExtractor.attributeNamesForParameters(method, parameters);
    }
  }
}
