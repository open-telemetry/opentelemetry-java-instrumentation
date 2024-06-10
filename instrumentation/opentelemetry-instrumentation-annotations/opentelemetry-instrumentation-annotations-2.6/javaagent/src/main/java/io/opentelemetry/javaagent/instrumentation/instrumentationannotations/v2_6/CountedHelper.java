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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class CountedHelper extends MetricsAnnotationHelper {

  private static final String COUNTED_DEFAULT_NAME = "method.invocation.count";
  private static final ConcurrentMap<String, LongCounter> COUNTERS = new ConcurrentHashMap<>();

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
    Counted countedAnnotation = method.getAnnotation(Counted.class);
    String metricName =
        (null == countedAnnotation.value() || countedAnnotation.value().isEmpty())
            ? COUNTED_DEFAULT_NAME
            : countedAnnotation.value();
    if (!COUNTERS.containsKey(metricName)) {
      synchronized (metricName) {
        if (!COUNTERS.containsKey(metricName)) {
          LongCounter longCounter = null;
          if (COUNTED_DEFAULT_NAME.equals(metricName)) {
            longCounter = METER.counterBuilder(metricName).build();
          } else {
            longCounter =
                METER
                    .counterBuilder(metricName)
                    .setDescription(countedAnnotation.description())
                    .setUnit(countedAnnotation.unit())
                    .build();
          }
          COUNTERS.put(metricName, longCounter);
        }
      }
    }
    return COUNTERS.get(metricName);
  }

  private CountedHelper() {}
}
