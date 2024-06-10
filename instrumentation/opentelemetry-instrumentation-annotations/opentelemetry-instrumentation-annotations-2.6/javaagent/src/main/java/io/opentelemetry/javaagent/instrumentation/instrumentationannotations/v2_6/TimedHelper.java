/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.instrumentationannotations.v2_6;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import application.io.opentelemetry.instrumentation.annotations.Timed;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.javaagent.instrumentation.instrumentationannotations.MethodRequest;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public final class TimedHelper extends MetricsAnnotationHelper {

  private static final String TIMED_DEFAULT_NAME = "method.invocation.duration";

  private static final ConcurrentMap<String, DoubleHistogram> HISTOGRAMS =
      new ConcurrentHashMap<>();

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
            doubleHistogram = METER.histogramBuilder(metricName).setUnit("ms").build();
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
