/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.scheduling;

import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import org.springframework.scheduling.support.ScheduledMethodRunnable;

public class SpringSchedulingTracer extends BaseTracer {
  private static final SpringSchedulingTracer TRACER = new SpringSchedulingTracer();

  public static SpringSchedulingTracer tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.spring-scheduling";
  }

  public String spanNameOnRun(Runnable runnable) {
    if (runnable instanceof ScheduledMethodRunnable) {
      ScheduledMethodRunnable scheduledMethodRunnable = (ScheduledMethodRunnable) runnable;
      return spanNameForMethod(scheduledMethodRunnable.getMethod());
    } else {
      return spanNameForClass(runnable.getClass()) + "/run";
    }
  }
}
