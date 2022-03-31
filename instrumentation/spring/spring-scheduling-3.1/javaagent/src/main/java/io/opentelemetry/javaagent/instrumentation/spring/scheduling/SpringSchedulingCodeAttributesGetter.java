/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.scheduling;

import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesGetter;
import org.springframework.scheduling.support.ScheduledMethodRunnable;

public class SpringSchedulingCodeAttributesGetter implements CodeAttributesGetter<Runnable> {

  @Override
  public Class<?> codeClass(Runnable runnable) {
    if (runnable instanceof ScheduledMethodRunnable) {
      ScheduledMethodRunnable scheduledMethodRunnable = (ScheduledMethodRunnable) runnable;
      return scheduledMethodRunnable.getMethod().getDeclaringClass();
    } else {
      return runnable.getClass();
    }
  }

  @Override
  public String methodName(Runnable runnable) {
    if (runnable instanceof ScheduledMethodRunnable) {
      ScheduledMethodRunnable scheduledMethodRunnable = (ScheduledMethodRunnable) runnable;
      return scheduledMethodRunnable.getMethod().getName();
    } else {
      return "run";
    }
  }
}
