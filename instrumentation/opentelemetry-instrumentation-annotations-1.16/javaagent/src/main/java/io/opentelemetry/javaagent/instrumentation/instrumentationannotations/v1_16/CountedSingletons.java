/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.instrumentationannotations.v1_16;

import static io.opentelemetry.javaagent.instrumentation.instrumentationannotations.v1_16.AnnotationSingletons.INSTRUMENTATION_NAME;
import static java.util.logging.Level.FINE;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.api.semconv.util.SpanNames;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class CountedSingletons {

  private static final String COUNTED_ANNOTATION_NAME =
      "io.opentelemetry.instrumentation.annotations.Counted";

  private static final Logger logger = Logger.getLogger(CountedSingletons.class.getName());
  private static final Meter meter = GlobalOpenTelemetry.get().getMeter(INSTRUMENTATION_NAME);
  private static final ClassValue<Map<Method, LongCounter>> counters =
      new ClassValue<Map<Method, LongCounter>>() {
        @Override
        protected Map<Method, LongCounter> computeValue(Class<?> type) {
          return new ConcurrentHashMap<>();
        }
      };

  public static void increment(Method method) {
    try {
      counters
          .get(method.getDeclaringClass())
          .computeIfAbsent(method, CountedSingletons::createCounter)
          .add(1);
    } catch (Throwable t) {
      logger.log(FINE, "failed to increment @Counted metric for " + method, t);
    }
  }

  private static LongCounter createCounter(Method method) {
    String metricName = createMetricName(method);
    return meter.counterBuilder(metricName).build();
  }

  private static String createMetricName(Method method) {
    for (Annotation annotation : method.getDeclaredAnnotations()) {
      Class<? extends Annotation> annotationType = annotation.annotationType();
      if (annotationType.getName().equals(COUNTED_ANNOTATION_NAME)) {
        try {
          String name = (String) annotationType.getMethod("value").invoke(annotation);
          if (!name.isEmpty()) {
            return name;
          }
        } catch (ReflectiveOperationException e) {
          logger.log(FINE, "failed to read @Counted value() for " + method, e);
        }
        break;
      }
    }
    // This is odd, indeed...but the naming rules are the same so we reuse it.
    return SpanNames.fromMethod(method);
  }

  private CountedSingletons() {}
}
