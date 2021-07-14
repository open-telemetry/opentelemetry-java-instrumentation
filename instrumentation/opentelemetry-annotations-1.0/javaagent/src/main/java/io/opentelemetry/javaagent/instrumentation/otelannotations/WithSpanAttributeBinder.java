/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.otelannotations;

import io.opentelemetry.instrumentation.api.annotation.support.AnnotationReflectionHelper;
import io.opentelemetry.instrumentation.api.annotation.support.AttributeBindings;
import io.opentelemetry.instrumentation.api.annotation.support.BaseAttributeBinder;
import io.opentelemetry.instrumentation.api.caching.Cache;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.function.Function;
import org.checkerframework.checker.nullness.qual.Nullable;

class WithSpanAttributeBinder extends BaseAttributeBinder {

  private static final Cache<Method, AttributeBindings> bindings =
      Cache.newBuilder().setWeakKeys().build();
  private static final Class<? extends Annotation> spanAttributeAnnotation;
  private static final Function<Annotation, String> spanAttributeValueFunction;

  static {
    ClassLoader classLoader = WithSpanAttributeBinder.class.getClassLoader();
    spanAttributeAnnotation =
        AnnotationReflectionHelper.forNameOrNull(
            classLoader, "io.opentelemetry.extension.annotations.SpanAttribute");
    if (spanAttributeAnnotation != null) {
      spanAttributeValueFunction = resolveSpanAttributeValue(spanAttributeAnnotation);
    } else {
      spanAttributeValueFunction = null;
    }
  }

  private static Function<Annotation, String> resolveSpanAttributeValue(
      Class<? extends Annotation> spanAttributeAnnotation) {
    try {
      return AnnotationReflectionHelper.bindAnnotationElementMethod(
          MethodHandles.lookup(), spanAttributeAnnotation, "value", String.class);
    } catch (Throwable exception) {
      return annotation -> "";
    }
  }

  @Override
  public AttributeBindings bind(Method method) {
    return spanAttributeAnnotation != null
        ? bindings.computeIfAbsent(method, super::bind)
        : EmptyAttributeBindings.INSTANCE;
  }

  @Override
  protected @Nullable String[] attributeNamesForParameters(Method method, Parameter[] parameters) {
    String[] attributeNames = new String[parameters.length];
    for (int i = 0; i < parameters.length; i++) {
      attributeNames[i] = attributeName(parameters[i]);
    }
    return attributeNames;
  }

  @Nullable
  private static String attributeName(Parameter parameter) {
    Annotation annotation = parameter.getDeclaredAnnotation(spanAttributeAnnotation);
    if (annotation == null) {
      return null;
    }
    String value = spanAttributeValueFunction.apply(annotation);
    if (!value.isEmpty()) {
      return value;
    } else if (parameter.isNamePresent()) {
      return parameter.getName();
    } else {
      return null;
    }
  }
}
