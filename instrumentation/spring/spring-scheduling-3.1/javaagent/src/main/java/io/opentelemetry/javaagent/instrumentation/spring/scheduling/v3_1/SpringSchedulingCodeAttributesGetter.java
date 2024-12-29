/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1;

import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesGetter;
import java.lang.reflect.Field;
import org.springframework.scheduling.support.ScheduledMethodRunnable;

public class SpringSchedulingCodeAttributesGetter implements CodeAttributesGetter<Runnable> {
  private static final Class<?> outcomeTrackingRunnableClass = getOutcomeTrackingRunnableClass();
  private static final Field outcomeTrackingRunnableField =
      getOutcomeTrackingRunnableField(outcomeTrackingRunnableClass);

  private static Class<?> getOutcomeTrackingRunnableClass() {
    try {
      return Class.forName("org.springframework.scheduling.config.Task$OutcomeTrackingRunnable");
    } catch (ClassNotFoundException exception) {
      return null;
    }
  }

  private static Field getOutcomeTrackingRunnableField(Class<?> clazz) {
    try {
      Field field = clazz.getDeclaredField("runnable");
      field.setAccessible(true);
      return field;
    } catch (Exception exception) {
      return null;
    }
  }

  private static Runnable unwrap(Runnable runnable) {
    if (outcomeTrackingRunnableClass != null
        && outcomeTrackingRunnableField != null
        && outcomeTrackingRunnableClass.isAssignableFrom(runnable.getClass())) {
      try {
        // task may be wrapped multiple times so
        return unwrap((Runnable) outcomeTrackingRunnableField.get(runnable));
      } catch (IllegalAccessException ignore) {
        // should not happen because setAccessible was called
      }
    }
    return runnable;
  }

  @Override
  public Class<?> getCodeClass(Runnable runnable) {
    runnable = unwrap(runnable);
    if (runnable instanceof ScheduledMethodRunnable) {
      ScheduledMethodRunnable scheduledMethodRunnable = (ScheduledMethodRunnable) runnable;
      return scheduledMethodRunnable.getMethod().getDeclaringClass();
    } else {
      return runnable.getClass();
    }
  }

  @Override
  public String getMethodName(Runnable runnable) {
    runnable = unwrap(runnable);
    if (runnable instanceof ScheduledMethodRunnable) {
      ScheduledMethodRunnable scheduledMethodRunnable = (ScheduledMethodRunnable) runnable;
      return scheduledMethodRunnable.getMethod().getName();
    } else {
      return "run";
    }
  }
}
