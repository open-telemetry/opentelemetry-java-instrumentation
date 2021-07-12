/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.otelannotations;

import io.opentelemetry.instrumentation.api.annotation.support.AttributeBindings;
import io.opentelemetry.instrumentation.api.annotation.support.BaseAttributeBinder;
import io.opentelemetry.instrumentation.api.caching.Cache;
import java.lang.annotation.Annotation;
import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
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
    spanAttributeAnnotation = resolveSpanAttributeAnnotationClass();
    if (spanAttributeAnnotation != null) {
      try {
        spanAttributeValueFunction = bindSpanAttributeValueMethod(spanAttributeAnnotation);
      } catch (Throwable exception) {
        throw new IllegalStateException(exception);
      }
    } else {
      spanAttributeValueFunction = null;
    }
  }

  private static Class<? extends Annotation> resolveSpanAttributeAnnotationClass() {
    try {
      return Class.forName("io.opentelemetry.extension.annotations.SpanAttribute")
          .asSubclass(Annotation.class);
    } catch (Exception exception) {
      return null;
    }
  }

  private static Function<Annotation, String> bindSpanAttributeValueMethod(
      Class<? extends Annotation> spanAttributeAnnotation) throws Throwable {

    Method valueMethod = spanAttributeAnnotation.getDeclaredMethod("value");
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    MethodHandle valueHandle = lookup.unreflect(valueMethod);

    CallSite callSite =
        LambdaMetafactory.metafactory(
            lookup,
            "apply",
            MethodType.methodType(Function.class),
            MethodType.methodType(Object.class, Object.class),
            valueHandle,
            MethodType.methodType(String.class, spanAttributeAnnotation));

    MethodHandle factory = callSite.getTarget();

    @SuppressWarnings("unchecked")
    Function<Annotation, String> function = (Function<Annotation, String>) factory.invoke();
    return function;
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
