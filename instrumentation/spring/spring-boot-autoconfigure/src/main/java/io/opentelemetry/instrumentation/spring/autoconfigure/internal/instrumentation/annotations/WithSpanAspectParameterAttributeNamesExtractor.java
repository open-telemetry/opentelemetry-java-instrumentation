/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.annotations;

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.api.annotation.support.ParameterAttributeNamesExtractor;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import javax.annotation.Nullable;
import org.springframework.core.ParameterNameDiscoverer;

class WithSpanAspectParameterAttributeNamesExtractor implements ParameterAttributeNamesExtractor {

  private final ParameterNameDiscoverer parameterNameDiscoverer;
  private final SpanAttributeNameSupplier spanAttributeNameSupplier;

  public WithSpanAspectParameterAttributeNamesExtractor(
      ParameterNameDiscoverer parameterNameDiscoverer,
      SpanAttributeNameSupplier spanAttributeNameSupplier) {
    this.parameterNameDiscoverer = parameterNameDiscoverer;
    this.spanAttributeNameSupplier = spanAttributeNameSupplier;
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
  private String attributeName(Parameter parameter, String[] parameterNames, int index) {
    String annotationValue = spanAttributeNameSupplier.spanAttributeName(parameter);
    if (annotationValue == null) {
      return null;
    }
    if (!annotationValue.isEmpty()) {
      return annotationValue;
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

  interface SpanAttributeNameSupplier {

    @Nullable
    String spanAttributeName(Parameter parameter);
  }

  static final class InstrumentationAnnotationAttributeNameSupplier
      implements SpanAttributeNameSupplier {

    @Nullable
    @Override
    public String spanAttributeName(Parameter parameter) {
      SpanAttribute annotation = parameter.getDeclaredAnnotation(SpanAttribute.class);
      return annotation == null ? null : annotation.value();
    }
  }
}
