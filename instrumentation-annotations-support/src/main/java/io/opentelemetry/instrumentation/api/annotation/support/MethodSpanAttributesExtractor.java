/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.annotation.support;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import java.lang.reflect.Method;
import javax.annotation.Nullable;

/** Extractor of {@link io.opentelemetry.api.common.Attributes} for a traced method. */
public final class MethodSpanAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  private final MethodExtractor<REQUEST> methodExtractor;
  private final MethodArgumentsExtractor<REQUEST> methodArgumentsExtractor;
  private final Cache<Method, AttributeBindings> cache;
  private final ParameterAttributeNamesExtractor parameterAttributeNamesExtractor;

  public static <REQUEST, RESPONSE> MethodSpanAttributesExtractor<REQUEST, RESPONSE> create(
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
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    Method method = methodExtractor.extract(request);
    AttributeBindings bindings =
        cache.computeIfAbsent(
            method, (Method m) -> AttributeBindings.bind(m, parameterAttributeNamesExtractor));
    if (!bindings.isEmpty()) {
      Object[] args = methodArgumentsExtractor.extract(request);
      bindings.apply(attributes, args);
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {}
}
