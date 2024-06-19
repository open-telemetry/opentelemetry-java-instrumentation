/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.instrumentationannotations.v2_6;

import application.io.opentelemetry.instrumentation.annotations.Counted;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.annotation.support.async.AsyncOperationEndSupport;
import io.opentelemetry.javaagent.instrumentation.instrumentationannotations.MethodRequest;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class CountedHelper extends MetricsAnnotationHelper {

  private static final ClassValue<Map<Method, LongCounter>> counters =
      new ClassValue<Map<Method, LongCounter>>() {
        @Override
        protected Map<Method, LongCounter> computeValue(Class<?> type) {
          return new ConcurrentHashMap<>();
        }
      };

  public static Object recordCountWithAttributes(
      MethodRequest methodRequest, Object returnValue, Throwable throwable) {
    return recordCount(
        methodRequest.method(),
        returnValue,
        throwable,
        attributesBuilder -> extractMetricAttributes(methodRequest, attributesBuilder));
  }

  public static Object recordCount(Method method, Object returnValue, Throwable throwable) {
    return recordCount(method, returnValue, throwable, attributesBuilder -> {});
  }

  private static Object recordCount(
      Method method,
      Object returnValue,
      Throwable throwable,
      Consumer<AttributesBuilder> additionalAttributes) {
    AsyncOperationEndSupport<Method, Object> operationEndSupport =
        AsyncOperationEndSupport.create(
            (context, method1, object, error) -> {
              Counted countedAnnotation = method1.getAnnotation(Counted.class);
              AttributesBuilder attributesBuilder =
                  getCommonAttributesBuilder(countedAnnotation, object, error);
              additionalAttributes.accept(attributesBuilder);
              getCounter(method1).add(1, attributesBuilder.build());
            },
            Object.class,
            method.getReturnType());
    return operationEndSupport.asyncEnd(Context.current(), method, returnValue, throwable);
  }

  private static AttributesBuilder getCommonAttributesBuilder(
      Counted countedAnnotation, Object returnValue, Throwable throwable) {
    AttributesBuilder attributesBuilder = Attributes.builder();
    extractAdditionAttributes(countedAnnotation.additionalAttributes(), attributesBuilder);
    extractReturnValue(countedAnnotation, returnValue, attributesBuilder);
    extractException(throwable, attributesBuilder);
    return attributesBuilder;
  }

  private static void extractException(Throwable throwable, AttributesBuilder attributesBuilder) {
    if (null != throwable) {
      attributesBuilder.put("exception", throwable.getClass().getName());
    }
  }

  private static void extractReturnValue(
      Counted countedAnnotation, Object returnValue, AttributesBuilder attributesBuilder) {
    if (returnValue != null && !countedAnnotation.returnValueAttribute().isEmpty()) {
      attributesBuilder.put(countedAnnotation.returnValueAttribute(), returnValue.toString());
    }
  }

  private static LongCounter getCounter(Method method) {
    return counters
        .get(method.getDeclaringClass())
        .computeIfAbsent(
            method,
            m -> {
              Counted countedAnnotation = m.getAnnotation(Counted.class);
              return METER
                  .counterBuilder(countedAnnotation.value())
                  .setDescription(countedAnnotation.description())
                  .setUnit(countedAnnotation.unit())
                  .build();
            });
  }

  private CountedHelper() {}
}
