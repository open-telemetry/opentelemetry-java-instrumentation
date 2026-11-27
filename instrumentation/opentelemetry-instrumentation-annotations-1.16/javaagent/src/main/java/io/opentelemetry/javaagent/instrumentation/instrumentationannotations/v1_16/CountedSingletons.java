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
import io.opentelemetry.api.metrics.MeterBuilder;
import io.opentelemetry.instrumentation.api.internal.ClassNames;
import io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

public class CountedSingletons {

  private static final String COUNTED_ANNOTATION_NAME =
      "application.io.opentelemetry.instrumentation.annotations.Counted";
  private static final Logger logger = Logger.getLogger(CountedSingletons.class.getName());
  private static final Meter meter = createMeter();
  private static final ClassValue<Map<Method, LongCounter>> counters =
      new ClassValue<Map<Method, LongCounter>>() {
        @Override
        protected Map<Method, LongCounter> computeValue(Class<?> type) {
          return new ConcurrentHashMap<>();
        }
      };
  private static final ClassValue<Field> continuationLabels =
      new ClassValue<Field>() {
        @Override
        @Nullable
        protected Field computeValue(Class<?> type) {
          try {
            Field label = type.getDeclaredField("label");
            if (label.getType() != int.class) {
              return null;
            }
            label.setAccessible(true);
            return label;
          } catch (NoSuchFieldException ignored) {
            // A caller's continuation need not have a state machine.
            return null;
          } catch (Throwable t) {
            logger.log(FINE, "failed to read Kotlin continuation label for " + type, t);
            return null;
          }
        }
      };
  private static final Pattern INVALID_CHARACTERS = Pattern.compile("[^a-zA-Z0-9_./-]");

  public static void increment(Method method, @Nullable Object continuation) {
    try {
      if (continuation != null) {
        Field label = continuationLabels.get(continuation.getClass());
        // Kotlin sets the sign bit before reentering a suspend method on resumption. A recursive
        // call has the sign bit cleared by the method's prologue and must still be counted.
        if (label != null && label.getInt(continuation) < 0) {
          return;
        }
      }
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

  private static Meter createMeter() {
    MeterBuilder meterBuilder =
        GlobalOpenTelemetry.get().getMeterProvider().meterBuilder(INSTRUMENTATION_NAME);
    String version = EmbeddedInstrumentationProperties.findVersion(INSTRUMENTATION_NAME);
    if (version != null) {
      meterBuilder.setInstrumentationVersion(version);
    }
    return meterBuilder.build();
  }

  private static String createMetricName(Method method) {
    String metricName = getConfiguredMetricName(method);
    if (!metricName.isEmpty()) {
      return metricName;
    }
    return buildDefaultMetricName(method);
  }

  private static String getConfiguredMetricName(Method method) {
    // Avoid linking Counted directly so that older annotation versions keep passing muzzle.
    for (Annotation annotation : method.getDeclaredAnnotations()) {
      if (annotation.annotationType().getName().equals(COUNTED_ANNOTATION_NAME)) {
        try {
          return (String) annotation.annotationType().getMethod("value").invoke(annotation);
        } catch (ReflectiveOperationException e) {
          logger.log(FINE, "failed to read @Counted metric name for " + method, e);
        }
      }
    }
    return "";
  }

  private static String buildDefaultMetricName(Method method) {
    String metricName = ClassNames.simpleName(method.getDeclaringClass()) + "." + method.getName();
    return INVALID_CHARACTERS.matcher(metricName).replaceAll("_");
  }

  private CountedSingletons() {}
}
