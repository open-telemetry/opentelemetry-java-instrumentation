/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.spring.scheduling;

import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import org.springframework.scheduling.support.ScheduledMethodRunnable;

public class SpringSchedulingTracer extends BaseTracer {
  public static final SpringSchedulingTracer TRACER = new SpringSchedulingTracer();

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.spring-scheduling-3.1";
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
