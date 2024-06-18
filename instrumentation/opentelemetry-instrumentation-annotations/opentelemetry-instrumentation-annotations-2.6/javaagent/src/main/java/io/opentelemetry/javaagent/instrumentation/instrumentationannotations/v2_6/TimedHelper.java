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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class TimedHelper extends MetricsAnnotationHelper {

  private static final ClassValue<Map<Method, DoubleHistogram>> histograms =
      new ClassValue<Map<Method, DoubleHistogram>>() {
        @Override
        protected Map<Method, DoubleHistogram> computeValue(Class<?> type) {
          return new ConcurrentHashMap<>();
        }
      };

  public static void recordHistogramWithAttributes(
      MethodRequest methodRequest, Throwable throwable, Object returnValue, long startNanoTime) {
    Timed timedAnnotation = methodRequest.method().getAnnotation(Timed.class);
    AttributesBuilder attributesBuilder =
        getCommonAttributeBuilder(throwable, returnValue, timedAnnotation);
    double duration = getTransformedDuration(startNanoTime, timedAnnotation);
    extractMetricAttributes(methodRequest, attributesBuilder);
    getHistogram(methodRequest.method()).record(duration, attributesBuilder.build());
  }

  public static void recordHistogram(
      Method method, Throwable throwable, Object returnValue, long startNanoTime) {
    Timed timedAnnotation = method.getAnnotation(Timed.class);
    AttributesBuilder attributesBuilder =
        getCommonAttributeBuilder(throwable, returnValue, timedAnnotation);
    double duration = getTransformedDuration(startNanoTime, timedAnnotation);
    getHistogram(method).record(duration, attributesBuilder.build());
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
    return unit.convert(nanoDelta, NANOSECONDS);
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

  private static DoubleHistogram getHistogram(Method method) {
    return histograms
        .get(method.getDeclaringClass())
        .computeIfAbsent(
            method,
            m -> {
              Timed timedAnnotation = m.getAnnotation(Timed.class);
              String unitStr = extractUnitStr(timedAnnotation);
              return METER
                  .histogramBuilder(timedAnnotation.value())
                  .setDescription(timedAnnotation.description())
                  .setUnit(unitStr)
                  .build();
            });
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
