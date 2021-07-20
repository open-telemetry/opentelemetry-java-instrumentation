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
public class MethodSpanAttributesExtractor<REQUEST, RESPONSE>
    extends AttributesExtractor<REQUEST, RESPONSE> {

  private final BaseAttributeBinder binder;
  private final MethodExtractor<REQUEST> methodExtractor;
  private final MethodArgumentsExtractor<REQUEST> methodArgumentsExtractor;
  private final Cache<Method, AttributeBindings> cache;

  /** Returns a new {@link MethodSpanAttributesExtractorBuilder}. */
  public static <REQUEST, RESPONSE>
      MethodSpanAttributesExtractorBuilder<REQUEST, RESPONSE> newBuilder(
          MethodExtractor<REQUEST> methodResolver) {

    return new MethodSpanAttributesExtractorBuilder<>(methodResolver);
  }

  MethodSpanAttributesExtractor(MethodSpanAttributesExtractorBuilder<REQUEST, RESPONSE> builder) {
    this.methodExtractor = builder.methodExtractor;
    this.methodArgumentsExtractor = builder.methodArgumentsExtractor;
    this.cache = builder.cache;
    this.binder = new MethodSpanAttributeBinder(builder.parameterAttributeNamesExtractor);
  }

  @Override
  protected void onStart(AttributesBuilder attributes, REQUEST request) {
    Method method = methodExtractor.extract(request);
    AttributeBindings bindings;
    if (cache != null) {
      bindings = cache.computeIfAbsent(method, binder::bind);
    } else {
      bindings = binder.bind(method);
    }
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
