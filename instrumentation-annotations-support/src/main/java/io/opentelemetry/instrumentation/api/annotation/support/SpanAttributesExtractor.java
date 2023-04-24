/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.annotation.support;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import java.lang.reflect.Method;

/** Extractor of {@link io.opentelemetry.api.common.Attributes} for a traced method. */
public final class SpanAttributesExtractor {

  private final Cache<Method, AttributeBindings> cache;
  private final ParameterAttributeNamesExtractor parameterAttributeNamesExtractor;

  public static SpanAttributesExtractor create(
      ParameterAttributeNamesExtractor parameterAttributeNamesExtractor) {
    return new SpanAttributesExtractor(parameterAttributeNamesExtractor, new MethodCache<>());
  }

  SpanAttributesExtractor(
      ParameterAttributeNamesExtractor parameterAttributeNamesExtractor,
      Cache<Method, AttributeBindings> cache) {
    this.parameterAttributeNamesExtractor = parameterAttributeNamesExtractor;
    this.cache = cache;
  }

  public Attributes extract(Method method, Object[] args) {
    AttributesBuilder attributes = Attributes.builder();
    AttributeBindings bindings =
        cache.computeIfAbsent(
            method, (Method m) -> AttributeBindings.bind(m, parameterAttributeNamesExtractor));
    if (!bindings.isEmpty()) {
      bindings.apply(attributes, args);
    }
    return attributes.build();
  }
}
