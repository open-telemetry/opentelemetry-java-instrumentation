/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.annotation.support;

import io.opentelemetry.instrumentation.api.caching.Cache;
import java.lang.reflect.Method;

public final class MethodSpanAttributesExtractorBuilder<REQUEST, RESPONSE> {
  MethodExtractor<REQUEST> methodExtractor;
  MethodArgumentsExtractor<REQUEST> methodArgumentsExtractor;
  Cache<Method, AttributeBindings> cache;
  ParameterAttributeNamesExtractor parameterAttributeNamesExtractor;

  public MethodSpanAttributesExtractorBuilder(MethodExtractor<REQUEST> methodExtractor) {
    this.methodExtractor = methodExtractor;
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
      MethodArgumentsExtractor<REQUEST> methodArgumentsExtractor) {
    this.methodArgumentsExtractor = methodArgumentsExtractor;
    return new MethodSpanAttributesExtractor<>(this);
  }
}
