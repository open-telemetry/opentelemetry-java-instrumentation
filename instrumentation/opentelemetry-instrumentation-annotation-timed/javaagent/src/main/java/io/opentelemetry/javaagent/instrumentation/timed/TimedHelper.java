/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.timed;

import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import application.io.opentelemetry.instrumentation.annotations.MetricAttribute;
import application.io.opentelemetry.instrumentation.annotations.Timed;
// import io.opentelemetry.javaagent.instrumentation.timed.annotations.Timed;
// import io.opentelemetry.javaagent.instrumentation.timed.annotations.MetricAttribute;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.internal.StringUtils;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.Meter;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public final class TimedHelper {

  private static final String INSTRUMENTATION_NAME =
      "io.opentelemetry.opentelemetry-instrumentation-annotation-timed";
  private static final String TIMED_DEFAULT_NAME = "method.invocation.duration";

  private static final ConcurrentMap<String, DoubleHistogram> HISTOGRAMS =
      new ConcurrentHashMap<>();

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

  public static void recordHistogramWithAttributes(
      MethodRequest methodRequest, Throwable throwable, Object returnValue, long startNanoTime) {
    Timed timedAnnotation = methodRequest.method().getAnnotation(Timed.class);
    AttributesBuilder attributesBuilder =
        getCommonAttributeBuilder(throwable, returnValue, timedAnnotation);
    double duration = getTransformedDuration(startNanoTime, timedAnnotation);
    extractMetricAttributes(methodRequest, attributesBuilder);
    getHistogram(timedAnnotation).record(duration, attributesBuilder.build());
  }

  public static void recordHistogram(
      Method method, Throwable throwable, Object returnValue, long startNanoTime) {
    Timed timedAnnotation = method.getAnnotation(Timed.class);
    AttributesBuilder attributesBuilder =
        getCommonAttributeBuilder(throwable, returnValue, timedAnnotation);
    double duration = getTransformedDuration(startNanoTime, timedAnnotation);
    getHistogram(timedAnnotation).record(duration, attributesBuilder.build());
  }

  private static AttributesBuilder getCommonAttributeBuilder(
      Throwable throwable, Object returnValue, Timed timedAnnotation) {
    AttributesBuilder attributesBuilder = Attributes.builder();
    extractAdditionAttributes(timedAnnotation.additionalAttributes(), attributesBuilder);
    extractReturnValue(timedAnnotation, returnValue, attributesBuilder);
    extractException(throwable, attributesBuilder);
    return attributesBuilder;
  }

  private static double getTransformedDuration(long startNanoTime, Timed timedAnnotation) {
    TimeUnit unit = extractTimeUnit(timedAnnotation);
    long nanoDelta = System.nanoTime() - startNanoTime;
    double duration = unit.convert(nanoDelta, NANOSECONDS);
    return duration;
  }

  private static void extractException(Throwable throwable, AttributesBuilder attributesBuilder) {
    if (null != throwable) {
      attributesBuilder.put("exception", throwable.getClass().getName());
    }
  }

  private static void extractReturnValue(
      Timed countedAnnotation, Object returnValue, AttributesBuilder attributesBuilder) {
    if (null != countedAnnotation.returnValueAttribute()
        && !countedAnnotation.returnValueAttribute().isEmpty()) {
      attributesBuilder.put(countedAnnotation.returnValueAttribute(), returnValue.toString());
    }
  }

  private static DoubleHistogram getHistogram(Timed timedAnnotation) {
    String metricName =
        (null == timedAnnotation.value() || timedAnnotation.value().isEmpty())
            ? TIMED_DEFAULT_NAME
            : timedAnnotation.value();
    if (!HISTOGRAMS.containsKey(metricName)) {
      synchronized (metricName) {
        if (!HISTOGRAMS.containsKey(metricName)) {
          DoubleHistogram doubleHistogram = null;
          if (TIMED_DEFAULT_NAME.equals(metricName)) {
            doubleHistogram = METER.histogramBuilder(metricName).build();
          } else {
            String unitStr = extractUnitStr(timedAnnotation);
            doubleHistogram =
                METER
                    .histogramBuilder(metricName)
                    .setDescription(timedAnnotation.description())
                    .setUnit(unitStr)
                    .build();
          }
          HISTOGRAMS.put(metricName, doubleHistogram);
        }
      }
    }
    return HISTOGRAMS.get(metricName);
  }

  private static TimeUnit extractTimeUnit(Timed timedAnnotation) {
    if (null == timedAnnotation.unit()) {
      return TimeUnit.MILLISECONDS;
    }
    return timedAnnotation.unit();
  }

  private static String extractUnitStr(Timed timedAnnotation) {
    switch (timedAnnotation.unit()) {
      case NANOSECONDS:
        return "ns";
      case MICROSECONDS:
        return "µs";
      case SECONDS:
        return "s";
      default:
        return "ms";
    }
  }

  private TimedHelper() {}
}
