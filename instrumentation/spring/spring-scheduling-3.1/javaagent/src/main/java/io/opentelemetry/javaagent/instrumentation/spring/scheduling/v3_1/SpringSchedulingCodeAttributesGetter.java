/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1;

import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesGetter;
import java.lang.reflect.Field;
import javax.annotation.Nullable;
import org.springframework.scheduling.support.ScheduledMethodRunnable;

public class SpringSchedulingCodeAttributesGetter implements CodeAttributesGetter<Runnable> {
  @Nullable
  private static final Class<?> OUTCOME_TRACKING_RUNNABLE_CLASS = getOutcomeTrackingRunnableClass();

  @Nullable
  private static final Field OUTCOME_TRACKING_RUNNABLE_FIELD =
      getOutcomeTrackingRunnableField(OUTCOME_TRACKING_RUNNABLE_CLASS);

  @Nullable
  private static Class<?> getOutcomeTrackingRunnableClass() {
    try {
      return Class.forName("org.springframework.scheduling.config.Task$OutcomeTrackingRunnable");
    } catch (ClassNotFoundException exception) {
      return null;
    }
  }

  @Nullable
  private static Field getOutcomeTrackingRunnableField(@Nullable Class<?> clazz) {
    if (clazz == null) {
      return null;
    }
    try {
      Field field = clazz.getDeclaredField("runnable");
      field.setAccessible(true);
      return field;
    } catch (Exception exception) {
      return null;
    }
  }

  private static Runnable unwrap(Runnable runnable) {
    if (OUTCOME_TRACKING_RUNNABLE_CLASS != null
        && OUTCOME_TRACKING_RUNNABLE_FIELD != null
        && OUTCOME_TRACKING_RUNNABLE_CLASS.isAssignableFrom(runnable.getClass())) {
      try {
        return unwrap((Runnable) OUTCOME_TRACKING_RUNNABLE_FIELD.get(runnable));
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
