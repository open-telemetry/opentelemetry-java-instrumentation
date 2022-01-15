/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.aspects;

import io.opentelemetry.extension.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.api.annotation.support.ParameterAttributeNamesExtractor;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import javax.annotation.Nullable;
import org.springframework.core.ParameterNameDiscoverer;

class WithSpanAspectParameterAttributeNamesExtractor implements ParameterAttributeNamesExtractor {
  private final ParameterNameDiscoverer parameterNameDiscoverer;

  public WithSpanAspectParameterAttributeNamesExtractor(
      ParameterNameDiscoverer parameterNameDiscoverer) {
    this.parameterNameDiscoverer = parameterNameDiscoverer;
  }

  @Override
  @Nullable
  public String[] extract(Method method, Parameter[] parameters) {
    String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
    String[] attributeNames = new String[parameters.length];

    for (int i = 0; i < parameters.length; i++) {
      attributeNames[i] = attributeName(parameters[i], parameterNames, i);
    }
    return attributeNames;
  }

  @Nullable
  private static String attributeName(Parameter parameter, String[] parameterNames, int index) {
    SpanAttribute annotation = parameter.getDeclaredAnnotation(SpanAttribute.class);
    if (annotation == null) {
      return null;
    }
    String value = annotation.value();
    if (!value.isEmpty()) {
      return value;
    }
    if (parameterNames != null && index < parameterNames.length) {
      String parameterName = parameterNames[index];
      if (parameterName != null && !parameterName.isEmpty()) {
        return parameterName;
      }
    }
    if (parameter.isNamePresent()) {
      return parameter.getName();
    }
    return null;
  }
}
