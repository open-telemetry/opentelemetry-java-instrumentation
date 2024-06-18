/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.instrumentationannotations.v2_6;

import application.io.opentelemetry.instrumentation.annotations.Counted;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.javaagent.instrumentation.instrumentationannotations.MethodRequest;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CountedHelper extends MetricsAnnotationHelper {

  private static final ClassValue<Map<Method, LongCounter>> counters =
      new ClassValue<Map<Method, LongCounter>>() {
        @Override
        protected Map<Method, LongCounter> computeValue(Class<?> type) {
          return new ConcurrentHashMap<>();
        }
      };

  public static void recordCountWithAttributes(
      MethodRequest methodRequest, Object returnValue, Throwable throwable) {
    Counted countedAnnotation = methodRequest.method().getAnnotation(Counted.class);
    AttributesBuilder attributesBuilder =
        getCommonAttributesBuilder(countedAnnotation, returnValue, throwable);
    extractMetricAttributes(methodRequest, attributesBuilder);
    getCounter(methodRequest.method()).add(1, attributesBuilder.build());
  }

  public static void recordCount(Method method, Object returnValue, Throwable throwable) {
    Counted countedAnnotation = method.getAnnotation(Counted.class);
    AttributesBuilder attributesBuilder =
        getCommonAttributesBuilder(countedAnnotation, returnValue, throwable);
    getCounter(method).add(1, attributesBuilder.build());
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
    if (null != countedAnnotation.returnValueAttribute()
        && !countedAnnotation.returnValueAttribute().isEmpty()) {
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
