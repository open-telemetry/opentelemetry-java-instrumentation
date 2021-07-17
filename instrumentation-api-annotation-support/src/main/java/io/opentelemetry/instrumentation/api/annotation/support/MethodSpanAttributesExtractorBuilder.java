/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.annotation.support;

import io.opentelemetry.instrumentation.api.caching.Cache;
import java.lang.reflect.Method;
import java.util.function.Function;

public final class MethodSpanAttributesExtractorBuilder<REQUEST, RESPONSE> {
  private static final ParameterAttributeNamesExtractor NOOP_ATTRIBUTE_NAME_EXTRACTOR =
      (method, parameters) -> null;

  Function<REQUEST, Method> methodResolver;
  Function<REQUEST, Object[]> argsResolver;
  Cache<Method, AttributeBindings> cache;
  ParameterAttributeNamesExtractor parameterAttributeNamesExtractor = NOOP_ATTRIBUTE_NAME_EXTRACTOR;

  public MethodSpanAttributesExtractorBuilder(Function<REQUEST, Method> methodResolver) {
    this.methodResolver = methodResolver;
  }

  public MethodSpanAttributesExtractorBuilder<REQUEST, RESPONSE> setMethodCache(
      Cache<Method, AttributeBindings> cache) {
    this.cache = cache;
    return this;
  }

  public MethodSpanAttributesExtractorBuilder<REQUEST, RESPONSE>
      setParameterAttributeNamesExtractor(
          ParameterAttributeNamesExtractor parameterAttributeNamesExtractor) {
    this.parameterAttributeNamesExtractor = parameterAttributeNamesExtractor;
    return this;
  }

  public MethodSpanAttributesExtractor<REQUEST, RESPONSE> build(
      Function<REQUEST, Object[]> argsResolver) {
    this.argsResolver = argsResolver;
    return new MethodSpanAttributesExtractor<>(this);
  }
}
