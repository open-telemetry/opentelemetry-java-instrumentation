/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.counted;

import application.io.opentelemetry.instrumentation.annotations.Counted;
import application.io.opentelemetry.instrumentation.annotations.MetricAttribute;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.internal.StringUtils;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class CountedHelper {

  private static final String INSTRUMENTATION_NAME =
      "io.opentelemetry.opentelemetry-instrumentation-annotation-counted";
  private static final String COUNTED_DEFAULT_NAME = "method.invocation.count";
  private static final ConcurrentMap<String, LongCounter> COUNTERS = new ConcurrentHashMap<>();
  private static final Meter METER = GlobalOpenTelemetry.get().getMeter(INSTRUMENTATION_NAME);

  private static void extractMetricAttributes(
      MethodRequest methodRequest, AttributesBuilder attributesBuilder) {
    Parameter[] parameters = methodRequest.method().getParameters();
    for (int i = 0; i < parameters.length; i++) {
      if (parameters[i].isAnnotationPresent(MetricAttribute.class)) {
        MetricAttribute annotation = parameters[i].getAnnotation(MetricAttribute.class);
        String attributeKey = "";
        if (!StringUtils.isNullOrEmpty(annotation.value())) {
          attributeKey = annotation.value();
        } else if (!StringUtils.isNullOrEmpty(parameters[i].getName())) {
          attributeKey = parameters[i].getName();
        } else {
          continue;
        }
        attributesBuilder.put(attributeKey, methodRequest.args()[i].toString());
      }
    }
  }

  private static void extractAdditionAttributes(
      String[] attributes, AttributesBuilder attributesBuilder) {
    int length = attributes.length;
    for (int i = 0; i + 1 < length; i += 2) {
      attributesBuilder.put(attributes[i], attributes[i + 1]);
    }
  }

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
